package com.pingpaisa.app.ui.scan

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pingpaisa.app.PingPaisaApp
import com.pingpaisa.app.camera.LivePrice
import com.pingpaisa.app.data.ItemState
import com.pingpaisa.app.data.ImportedImage
import com.pingpaisa.app.data.PriceSource
import com.pingpaisa.app.data.Product
import com.pingpaisa.app.finance.AffordabilityCalculator
import com.pingpaisa.app.finance.Assessment
import com.pingpaisa.app.finance.Candidate
import com.pingpaisa.app.finance.FinancialProfile
import com.pingpaisa.app.finance.Priority
import com.pingpaisa.app.link.SharedContent
import com.pingpaisa.app.link.SharedLinkParser
import com.pingpaisa.app.recognition.AnalysisResult
import com.pingpaisa.app.recognition.FocusSpec
import com.pingpaisa.app.catalog.ProductClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    /** Exact same image / link already in the list: ask, never silently drop. */
    data class AskDuplicate(val token: String, val existingName: String) : UiEvent
    /** Analysis finished and looks like another item (same name and price). */
    data class AskPossibleDuplicate(val productId: String, val otherName: String) : UiEvent
}

@OptIn(FlowPreview::class)
class ScanViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app as PingPaisaApp
    private val images = ctx.imageStore
    private val analyzer = ctx.analyzer
    private val classifier = ctx.classifier
    val categories = ctx.categories

    private val _products = MutableStateFlow(ctx.productStore.load())
    val products: StateFlow<List<Product>> = _products

    val profile: StateFlow<FinancialProfile?> = ctx.profileStore.profile
    val profileIsPersistent: Boolean get() = ctx.profileStore.persistent

    /** Recomputed instantly whenever a price, priority or the product list changes. */
    val assessment: StateFlow<Assessment?> = combine(_products, profile) { list, p ->
        if (p == null || !p.isUsable()) null
        else AffordabilityCalculator.assess(
            p, list.filter { it.state != ItemState.PROCESSING }.map { Candidate(it.id, it.price, it.priority) },
            ctx.expenseHistory.lastCompleteMonthTotals()
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selectedId
    /** Timestamp of the last time a product was captured or tapped; the chip shows it for a few seconds. */
    val pinnedAt = MutableStateFlow(0L)

    val live = MutableStateFlow<LivePrice?>(null)

    private val eventChannel = Channel<UiEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private class Pending(val proceed: () -> Unit, val discard: () -> Unit)
    private val pending = HashMap<String, Pending>()
    private val gate = Semaphore(2)

    init {
        viewModelScope.launch(Dispatchers.IO) { _products.drop(1).debounce(400).collect { ctx.productStore.save(it) } }
    }

    // ---------- inputs ----------

    fun addGalleryImages(uris: List<Uri>) {
        uris.forEach { uri -> viewModelScope.launch { importAndAnalyze(null) { images.importUri(uri) } } }
    }

    fun addCapturedImage(file: File, focus: FocusSpec?) {
        viewModelScope.launch { importAndAnalyze(focus) { images.importFile(file) } }
    }

    /** Entry point for Android share: link, text with a link, plain text, and/or images. */
    fun handleShared(text: String?, subject: String?, uris: List<Uri>) {
        if (uris.isNotEmpty()) addGalleryImages(uris)
        val content = SharedLinkParser.parse(listOfNotNull(subject?.takeIf { it.isNotBlank() }, text).joinToString("\n")) ?: return
        val url = content.url
        if (url == null) { startText(content); return }
        val key = SharedLinkParser.normalizedKey(url)
        val dup = _products.value.firstOrNull { it.sourceUrl != null && SharedLinkParser.normalizedKey(it.sourceUrl) == key }
        if (dup == null) startLink(content) else {
            val token = "link:$key"
            pending[token] = Pending({ startLink(content) }, {})
            send(UiEvent.AskDuplicate(token, dup.name))
        }
    }

    // ---------- edits ----------

    fun select(id: String?) {
        _selectedId.value = id
        if (id != null) pinnedAt.value = System.currentTimeMillis()
    }

    fun updateProduct(id: String, name: String, price: Double?, category: String, subcategory: String, priority: Priority) {
        update(id) { p ->
            val edited = p.edited.toMutableSet()
            val priceChanged = price != p.price
            if (name.trim() != p.name) edited += "name"
            if (priceChanged) edited += "price"
            if (category != p.category || subcategory != p.subcategory) edited += "category"
            val cls = if (category != p.category || subcategory != p.subcategory) classifier.manual(category, subcategory) else null
            p.copy(
                name = name.trim().ifBlank { p.name }, nameIsGuess = if ("name" in edited) false else p.nameIsGuess,
                price = price, priceSource = if (priceChanged) (if (price == null) PriceSource.NONE else PriceSource.MANUAL) else p.priceSource,
                priceConfidence = if (priceChanged) null else p.priceConfidence,
                priceNote = if (priceChanged) null else p.priceNote,
                category = category, subcategory = subcategory,
                classSource = cls?.source ?: p.classSource, classConfidence = cls?.confidence ?: p.classConfidence,
                classEvidence = cls?.evidence ?: p.classEvidence,
                priority = priority, edited = edited,
                state = if (price == null) ItemState.NEEDS_PRICE else ItemState.READY,
                message = if (price != null) null else p.message
            )
        }
    }

    fun removeProduct(id: String) {
        val p = _products.value.firstOrNull { it.id == id } ?: return
        images.delete(p.imagePath)
        _products.update { l -> l.filterNot { it.id == id } }
        if (_selectedId.value == id) _selectedId.value = null
    }

    fun clearAll() {
        _products.value = emptyList()
        _selectedId.value = null
        images.clearAll()
    }

    fun saveProfile(p: FinancialProfile) = ctx.profileStore.save(p)

    fun resolveDuplicate(token: String, keep: Boolean) {
        pending.remove(token)?.let { if (keep) it.proceed() else it.discard() }
    }

    fun resolvePossibleDuplicate(productId: String, keep: Boolean) { if (!keep) removeProduct(productId) }

    // ---------- pipeline ----------

    private suspend fun importAndAnalyze(focus: FocusSpec?, block: () -> ImportedImage) {
        val img = try { withContext(Dispatchers.IO) { block() } } catch (e: Exception) {
            send(UiEvent.Message("Couldn't read that image.")); return
        }
        val dup = _products.value.firstOrNull { it.contentHash == img.hash }
        if (dup != null) {
            val token = "img:${img.path}"
            pending[token] = Pending({ startImage(img, focus) }, { images.delete(img.path) })
            send(UiEvent.AskDuplicate(token, dup.name))
        } else startImage(img, focus)
    }

    private fun startImage(img: ImportedImage, focus: FocusSpec?) {
        val id = begin(Product(imagePath = img.path, contentHash = img.hash, name = "Reading image…"))
        run(id) { analyzer.analyzeImage(img.path, focus) }
    }

    private fun startLink(content: SharedContent) {
        val id = begin(Product(sourceUrl = content.url, name = "Reading product page…"))
        run(id) { analyzer.analyzeLink(content) }
    }

    private fun startText(content: SharedContent) {
        val id = begin(Product(name = "Reading shared text…"))
        run(id) { analyzer.analyzeText(content) }
    }

    private fun begin(p: Product): String {
        _products.update { it + p }
        select(p.id)
        return p.id
    }

    private fun run(id: String, work: suspend () -> AnalysisResult) {
        viewModelScope.launch {
            val result = gate.withPermit { try { work() } catch (e: Exception) { null } }
            apply(id, result)
        }
    }

    private fun apply(id: String, r: AnalysisResult?) {
        if (_products.value.none { it.id == id }) return          // removed while analysing
        update(id) { p ->
            if (r == null) p.copy(
                name = "Unknown product", nameIsGuess = true, state = ItemState.NEEDS_INFO,
                message = "Couldn't analyse this. Enter the details yourself."
            ) else p.copy(
                name = r.name, nameIsGuess = r.nameIsGuess, brand = r.brand,
                price = r.price, priceSource = r.priceSource, priceConfidence = r.priceConfidence,
                priceCandidates = r.priceCandidates, priceNote = r.priceNote,
                category = r.classification.category, subcategory = r.classification.subcategory,
                classSource = r.classification.source, classConfidence = r.classification.confidence,
                classEvidence = r.classification.evidence,
                imagePath = r.imagePath ?: p.imagePath, contentHash = r.imageHash ?: p.contentHash,
                state = if (r.price == null) ItemState.NEEDS_PRICE else ItemState.READY, message = r.message
            )
        }
        val done = _products.value.firstOrNull { it.id == id } ?: return
        val price = done.price ?: return
        if (done.nameIsGuess) return
        val twin = _products.value.firstOrNull {
            it.id != id && it.price == price && it.state != ItemState.PROCESSING &&
                ProductClassifier.normalize(it.name) == ProductClassifier.normalize(done.name)
        }
        if (twin != null) send(UiEvent.AskPossibleDuplicate(id, twin.name))
    }

    private fun update(id: String, f: (Product) -> Product) {
        _products.update { list -> list.map { if (it.id == id) f(it) else it } }
    }

    private fun send(e: UiEvent) { eventChannel.trySend(e) }
}
