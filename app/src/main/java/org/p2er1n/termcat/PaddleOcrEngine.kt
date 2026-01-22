package org.p2er1n.termcat

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.os.Build
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class PaddleOcrEngine(private val context: Context) {
    @Volatile
    private var initState = InitState.UNINITIALIZED
    @Volatile
    private var ocrInstance: Any? = null
    private val initLock = Any()

    fun run(bitmap: Bitmap): String {
        return RUN_LOCK.withLock {
            if (!ensureInitialized()) {
                return@withLock ""
            }
            val instance = ocrInstance ?: return@withLock ""
            val runSync = instance.javaClass.methods.firstOrNull {
                it.name == "runSync" && it.parameterTypes.size == 1
            }
            if (runSync != null) {
                return@withLock extractSimpleText(runSync.invoke(instance, bitmap))
            }
            val run = instance.javaClass.methods.firstOrNull {
                it.name == "run" && it.parameterTypes.size == 2
            } ?: return@withLock ""
            val latch = CountDownLatch(1)
            var resultText = ""
            val callback = createRunCallback(run.parameterTypes[1]) { text ->
                resultText = text
                latch.countDown()
            }
            run.invoke(instance, bitmap, callback)
            latch.await(RUN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            resultText
        }
    }

    private fun ensureInitialized(): Boolean {
        if (initState == InitState.READY) return true
        if (initState == InitState.FAILED) return false
        synchronized(initLock) {
            if (initState == InitState.READY) return true
            if (initState == InitState.FAILED) return false
            initState = InitState.INITIALIZING
        }

        val instance = createOcrInstance()
        if (instance == null) {
            initState = InitState.FAILED
            return false
        }
        val config = createConfig()
        if (config == null) {
            initState = InitState.FAILED
            return false
        }

        val initSync = instance.javaClass.methods.firstOrNull {
            it.name == "initModelSync" && it.parameterTypes.size == 1
        }
        if (initSync != null) {
            val ok = (initSync.invoke(instance, config) as? Boolean) ?: false
            initState = if (ok) InitState.READY else InitState.FAILED
            if (ok) {
                ocrInstance = instance
            }
            return ok
        }

        val init = instance.javaClass.methods.firstOrNull {
            it.name == "initModel" && it.parameterTypes.size == 2
        }
        if (init == null) {
            initState = InitState.FAILED
            return false
        }
        val latch = CountDownLatch(1)
        var ok = false
        val callback = createInitCallback(init.parameterTypes[1]) { success ->
            ok = success
            latch.countDown()
        }
        init.invoke(instance, config, callback)
        latch.await(INIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        initState = if (ok) InitState.READY else InitState.FAILED
        if (ok) {
            ocrInstance = instance
        }
        return ok
    }

    private fun createOcrInstance(): Any? {
        for (className in OCR_CLASS_NAMES) {
            val clazz = runCatching { Class.forName(className) }.getOrNull() ?: continue
            val withContext = runCatching {
                clazz.getDeclaredConstructor(Context::class.java).newInstance(context)
            }.getOrNull()
            if (withContext != null) return withContext
            val noArg = runCatching { clazz.getDeclaredConstructor().newInstance() }.getOrNull()
            if (noArg != null) return noArg
            val getInstance = clazz.methods.firstOrNull { it.name == "getInstance" }
            if (getInstance != null) {
                val params = getInstance.parameterTypes
                val instance = if (params.isEmpty()) {
                    getInstance.invoke(null)
                } else if (params.size == 1 && params[0] == Context::class.java) {
                    getInstance.invoke(null, context)
                } else {
                    null
                }
                if (instance != null) return instance
            }
        }
        Log.e(TAG, "PaddleOCR init failed: OCR class not found.")
        return null
    }

    private fun createConfig(): Any? {
        val configClass = runCatching { Class.forName(OCR_CONFIG_CLASS) }.getOrNull() ?: return null
        val config = runCatching { configClass.getDeclaredConstructor().newInstance() }.getOrNull()
            ?: return null
        setField(config, "modelPath", MODEL_PATH)
        setField(config, "clsModelFilename", CLS_MODEL_FILENAME)
        setField(config, "detModelFilename", DET_MODEL_FILENAME)
        setField(config, "recModelFilename", REC_MODEL_FILENAME)
        setField(config, "isRunDet", true)
        setField(config, "isRunCls", true)
        setField(config, "isRunRec", true)
        setField(config, "isDrwwTextPositionBox", false)
        val cpuPowerMode = runCatching { Class.forName(CPU_POWER_MODE_CLASS) }.getOrNull()
        if (cpuPowerMode != null) {
            val field = runCatching { cpuPowerMode.getField(CPU_POWER_MODE_FULL) }.getOrNull()
            if (field != null) {
                setField(config, "cpuPowerMode", field.get(null))
            }
        }
        return config
    }

    private fun createInitCallback(callbackType: Class<*>, onDone: (Boolean) -> Unit): Any {
        return Proxy.newProxyInstance(callbackType.classLoader, arrayOf(callbackType)) { _, method, args ->
            when (method.name) {
                "onSuccess" -> onDone(true)
                "onFail" -> {
                    Log.e(
                        TAG,
                        "PaddleOCR init failed (supportedAbis=${Build.SUPPORTED_ABIS.joinToString()})",
                        args?.firstOrNull() as? Throwable
                    )
                    onDone(false)
                }
            }
            null
        }
    }

    private fun createRunCallback(callbackType: Class<*>, onDone: (String) -> Unit): Any {
        return Proxy.newProxyInstance(callbackType.classLoader, arrayOf(callbackType)) { _, method, args ->
            when (method.name) {
                "onSuccess" -> onDone(extractSimpleText(args?.firstOrNull()))
                "onFail" -> {
                    Log.e(TAG, "PaddleOCR run failed", args?.firstOrNull() as? Throwable)
                    onDone("")
                }
            }
            null
        }
    }

    private fun extractSimpleText(result: Any?): String {
        if (result == null) return ""
        val getter = result.javaClass.methods.firstOrNull { it.name == "getSimpleText" }
        if (getter != null) {
            return (getter.invoke(result) as? String).orEmpty()
        }
        val field = result.javaClass.fields.firstOrNull { it.name == "simpleText" }
        return if (field != null) (field.get(result) as? String).orEmpty() else ""
    }

    private fun setField(target: Any, fieldName: String, value: Any) {
        runCatching {
            val field = target.javaClass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(target, value)
        }
    }

    private enum class InitState {
        UNINITIALIZED,
        INITIALIZING,
        READY,
        FAILED
    }

    companion object {
        private const val TAG = "PaddleOcrEngine"
        private val RUN_LOCK = ReentrantLock()
        private const val OCR_CONFIG_CLASS = "com.equationl.paddleocr4android.OcrConfig"
        private const val CPU_POWER_MODE_CLASS = "com.equationl.paddleocr4android.CpuPowerMode"
        private const val CPU_POWER_MODE_FULL = "LITE_POWER_FULL"
        private const val MODEL_PATH = "models/ch_PP-OCRv4"
        private const val CLS_MODEL_FILENAME = "cls.nb"
        private const val DET_MODEL_FILENAME = "det.nb"
        private const val REC_MODEL_FILENAME = "rec.nb"
        private const val INIT_TIMEOUT_MS = 8000L
        private const val RUN_TIMEOUT_MS = 8000L
        private val OCR_CLASS_NAMES = listOf(
            "com.equationl.paddleocr4android.OCR",
            "com.equationl.paddleocr4android.Ocr",
            "com.equationl.paddleocr4android.OcrManager",
            "com.equationl.paddleocr4android.PaddleOCR"
        )
    }
}
