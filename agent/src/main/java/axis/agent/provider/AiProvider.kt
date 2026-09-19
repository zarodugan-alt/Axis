package axis.agent.provider

/**
 * Which wire protocol a provider speaks. AXIS supports the three dominant
 * shapes plus the two speech endpoints; everything else is "OpenAI-compatible"
 * (Groq, Mistral, OpenRouter, Together, local Ollama, …).
 */
enum class ApiStyle { OPENAI, GEMINI, ANTHROPIC, UNREAL_SPEECH, ELEVENLABS }

/**
 * One BYOK backend (spec §F5). [baseUrl] is the API root, not the full
 * endpoint — each protocol adapter appends its own path. Keys are never
 * stored here; this is static metadata only.
 */
data class AiProvider(
    val id: String,
    val label: String,
    val apiStyle: ApiStyle,
    val baseUrl: String,
    val models: List<String>,
    val defaultModel: String,
    val keyHelpUrl: String,
    /** Typical key prefix, used for soft validation only (never enforced). */
    val keyPrefix: String? = null,
    val freeTier: String? = null,
    /** Ollama-style local servers need no key at all. */
    val keyless: Boolean = false,
    val custom: Boolean = false,
    /** Short "what is this good for" line shown in the setup list. */
    val blurb: String = ""
) {
    val isSpeech: Boolean
        get() = apiStyle == ApiStyle.UNREAL_SPEECH || apiStyle == ApiStyle.ELEVENLABS

    val isChat: Boolean get() = !isSpeech
}

/**
 * Built-in catalogue. Model ids are conservative and long-lived — users can
 * override any of them per provider, and a wrong id surfaces as a provider
 * error in the usage dashboard rather than a crash.
 */
object ProviderCatalog {

    val groq = AiProvider(
        id = "groq",
        label = "Groq",
        apiStyle = ApiStyle.OPENAI,
        baseUrl = "https://api.groq.com/openai/v1",
        models = listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b-32768"),
        defaultModel = "llama-3.3-70b-versatile",
        keyHelpUrl = "https://console.groq.com/keys",
        keyPrefix = "gsk_",
        freeTier = "Free tier: generous rate limits",
        blurb = "Fastest responses (LPU inference). Best default."
    )

    val mistral = AiProvider(
        id = "mistral",
        label = "Mistral",
        apiStyle = ApiStyle.OPENAI,
        baseUrl = "https://api.mistral.ai/v1",
        models = listOf("mistral-large-latest", "mistral-small-latest", "open-mistral-nemo"),
        defaultModel = "mistral-small-latest",
        keyHelpUrl = "https://console.mistral.ai/api-keys",
        freeTier = "Free experiment tier available",
        blurb = "Strong European models, cheap small tier."
    )

    val gemini = AiProvider(
        id = "gemini",
        label = "Google Gemini",
        apiStyle = ApiStyle.GEMINI,
        baseUrl = "https://generativelanguage.googleapis.com/v1beta",
        models = listOf("gemini-1.5-flash", "gemini-1.5-flash-8b", "gemini-1.5-pro"),
        defaultModel = "gemini-1.5-flash",
        keyHelpUrl = "https://aistudio.google.com/app/apikey",
        keyPrefix = "AIza",
        freeTier = "Free tier in AI Studio",
        blurb = "Large context, free tier, good vision."
    )

    val openai = AiProvider(
        id = "openai",
        label = "OpenAI",
        apiStyle = ApiStyle.OPENAI,
        baseUrl = "https://api.openai.com/v1",
        models = listOf("gpt-4o-mini", "gpt-4o", "gpt-4.1-mini"),
        defaultModel = "gpt-4o-mini",
        keyHelpUrl = "https://platform.openai.com/api-keys",
        keyPrefix = "sk-",
        blurb = "Reference implementation, paid."
    )

    val anthropic = AiProvider(
        id = "anthropic",
        label = "Anthropic",
        apiStyle = ApiStyle.ANTHROPIC,
        baseUrl = "https://api.anthropic.com/v1",
        models = listOf("claude-3-5-haiku-latest", "claude-3-5-sonnet-latest"),
        defaultModel = "claude-3-5-haiku-latest",
        keyHelpUrl = "https://console.anthropic.com/settings/keys",
        keyPrefix = "sk-ant-",
        blurb = "Careful reasoning, excellent tool use."
    )

    val openrouter = AiProvider(
        id = "openrouter",
        label = "OpenRouter",
        apiStyle = ApiStyle.OPENAI,
        baseUrl = "https://openrouter.ai/api/v1",
        models = listOf(
            "meta-llama/llama-3.3-70b-instruct",
            "anthropic/claude-3.5-haiku",
            "google/gemini-flash-1.5"
        ),
        defaultModel = "meta-llama/llama-3.3-70b-instruct",
        keyHelpUrl = "https://openrouter.ai/keys",
        keyPrefix = "sk-or-",
        freeTier = "Free models available",
        blurb = "One key, hundreds of models (incl. free)."
    )

    val ollama = AiProvider(
        id = "ollama",
        label = "Local (Ollama)",
        apiStyle = ApiStyle.OPENAI,
        baseUrl = "http://127.0.0.1:11434/v1",
        models = listOf("llama3.2", "qwen2.5", "phi3.5"),
        defaultModel = "llama3.2",
        keyHelpUrl = "https://ollama.com/download",
        keyless = true,
        blurb = "Offline, private, no key. Needs Ollama on the device/network."
    )

    val unrealSpeech = AiProvider(
        id = "unreal",
        label = "Unreal Speech",
        apiStyle = ApiStyle.UNREAL_SPEECH,
        baseUrl = "https://api.v7.unrealspeech.com",
        models = listOf("Scarlett", "Dan", "Liv", "Will", "Amy", "Azure"),
        defaultModel = "Scarlett",
        keyHelpUrl = "https://unrealspeech.com/dashboard",
        freeTier = "Free tier each month",
        blurb = "Cheap, fast voice output for the voice HUD."
    )

    val elevenLabs = AiProvider(
        id = "elevenlabs",
        label = "ElevenLabs",
        apiStyle = ApiStyle.ELEVENLABS,
        baseUrl = "https://api.elevenlabs.io/v1",
        models = listOf("21m00Tcm4TlvDq8ikWAM", "EXAVITQu4vr4xnSDxMaL"),
        defaultModel = "21m00Tcm4TlvDq8ikWAM",
        keyHelpUrl = "https://elevenlabs.io/app/settings/api-keys",
        blurb = "Highest quality voices."
    )

    /** Custom OpenAI-compatible endpoint (self-hosted, vLLM, LM Studio, …). */
    val custom = AiProvider(
        id = "custom",
        label = "Custom endpoint",
        apiStyle = ApiStyle.OPENAI,
        baseUrl = "http://192.168.1.10:8000/v1",
        models = listOf("default"),
        defaultModel = "default",
        keyHelpUrl = "",
        keyless = true,
        custom = true,
        blurb = "Any OpenAI-compatible server on your LAN or VPN."
    )

    val builtins: List<AiProvider> = listOf(
        groq, mistral, gemini, openai, anthropic, openrouter, ollama, unrealSpeech, elevenLabs
    )

    val chatProviders: List<AiProvider> = builtins.filter { it.isChat }

    val speechProviders: List<AiProvider> = builtins.filter { it.isSpeech }

    fun byId(id: String): AiProvider? = builtins.firstOrNull { it.id == id }

    /** Routing strategies (spec §F6): how the gateway picks a provider. */
    enum class RoutingMode { MANUAL, AUTO, CHEAP_FIRST }

    /**
     * Orders chat providers for a request. [enabled] is the user's enabled
     * set in their preferred order, [mode] the strategy:
     *  - MANUAL: only the first enabled provider (no failover),
     *  - AUTO: the whole enabled list in order (failover on error),
     *  - CHEAP_FIRST: free-tier providers first, then the rest in order.
     */
    fun order(
        enabled: List<AiProvider>,
        mode: RoutingMode
    ): List<AiProvider> = when (mode) {
        RoutingMode.MANUAL -> enabled.take(1)
        RoutingMode.AUTO -> enabled
        RoutingMode.CHEAP_FIRST -> enabled.sortedBy { if (it.freeTier != null) 0 else 1 }
    }
}
