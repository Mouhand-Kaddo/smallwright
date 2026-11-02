package dev.smallwright.agent;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The model the person picked: an OpenRouter model with its listed price in US dollars per million
 * tokens, or a model at an OpenAI-compatible server, which lists no price.
 */
public final class ModelChoice {
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9~][A-Za-z0-9._:/~-]{0,127}");
    private static final BigDecimal MAX_PRICE = new BigDecimal("1000");
    private static final BigDecimal MILLION = new BigDecimal(1_000_000);
    static final int MAX_CONTEXT_TOKENS = 1 << 30;

    /** The context length assumed for a model whose listing gave none. */
    public static final int UNKNOWN_CONTEXT_TOKENS = 131_072;

    /**
     * Prompt tokens of a typical app run: 153,186 (130,048 of them cached) in one measured reading
     * list run of 15 requests with DeepSeek V4.1 Flash on the Galaxy A33, rounded to two figures.
     * Source: docs/validation/model-picker-a33-20261019.md.
     */
    static final long TYPICAL_PROMPT_TOKENS = 150_000;

    /**
     * Completion tokens of a typical app run: 13,101 in the same measured run, rounded to two
     * figures. Source: docs/validation/model-picker-a33-20261019.md.
     */
    static final long TYPICAL_COMPLETION_TOKENS = 13_000;

    /** The models known to build apps well here, by id. */
    public static final List<String> SUGGESTED = List.of(ModelClient.MODEL);

    public static final ModelChoice DEFAULT =
            new ModelChoice(
                    ModelClient.MODEL,
                    "deepseek/deepseek-v4.1-flash-20260910",
                    "DeepSeek: DeepSeek V4.1 Flash",
                    new BigDecimal("0.3"),
                    new BigDecimal("1.2"),
                    1_048_576,
                    true,
                    true,
                    943_718);

    public final ModelProvider provider;

    public final String id;

    /** The versioned name OpenRouter answers with; the same as {@link #id} when it has none. */
    public final String canonicalId;

    public final String name;

    /** The listed price; null at a model server, which lists none. */
    public final BigDecimal promptUsdPerMillion;

    /** The listed price; null at a model server, which lists none. */
    public final BigDecimal completionUsdPerMillion;

    /**
     * How many tokens one request and its reply may hold, as the provider lists it, or {@link
     * #UNKNOWN_CONTEXT_TOKENS}.
     */
    public final int contextTokens;

    /**
     * True when OpenRouter lists {@code reasoning} among the model's parameters, so the person can
     * set how much it thinks.
     */
    public final boolean reasoning;

    /** True when OpenRouter lists image among the model's inputs. */
    public final boolean readsImages;

    /**
     * The most tokens one reply may hold, as OpenRouter lists for the model's top provider; 0 when
     * it lists none.
     */
    public final int maxCompletionTokens;

    public ModelChoice(
            String id,
            String canonicalId,
            String name,
            BigDecimal promptUsdPerMillion,
            BigDecimal completionUsdPerMillion,
            int contextTokens) {
        this(
                id,
                canonicalId,
                name,
                promptUsdPerMillion,
                completionUsdPerMillion,
                contextTokens,
                false,
                false,
                0);
    }

    public ModelChoice(
            String id,
            String canonicalId,
            String name,
            BigDecimal promptUsdPerMillion,
            BigDecimal completionUsdPerMillion,
            int contextTokens,
            boolean reasoning,
            boolean readsImages,
            int maxCompletionTokens) {
        this(
                ModelProvider.OPENROUTER,
                id,
                canonicalId,
                name,
                promptUsdPerMillion,
                completionUsdPerMillion,
                contextTokens,
                reasoning,
                readsImages,
                maxCompletionTokens);
        if (!validPrice(promptUsdPerMillion) || !validPrice(completionUsdPerMillion))
            throw new IllegalArgumentException("Invalid model price");
    }

    /**
     * A model at an OpenAI-compatible server, by the name the server knows it by, with the context
     * it lists or {@link #UNKNOWN_CONTEXT_TOKENS}. Nothing says it reasons on request or reads
     * pictures, and no reply length is asked for.
     */
    public static ModelChoice atServer(ModelProvider server, String id, int contextTokens) {
        if (server == null || server.isOpenRouter())
            throw new IllegalArgumentException("Not a model server");
        return new ModelChoice(server, id, id, id, null, null, contextTokens, false, false, 0);
    }

    private ModelChoice(
            ModelProvider provider,
            String id,
            String canonicalId,
            String name,
            BigDecimal promptUsdPerMillion,
            BigDecimal completionUsdPerMillion,
            int contextTokens,
            boolean reasoning,
            boolean readsImages,
            int maxCompletionTokens) {
        if (id == null || !ID.matcher(id).matches())
            throw new IllegalArgumentException("Invalid model id");
        if (canonicalId == null || !ID.matcher(canonicalId).matches())
            throw new IllegalArgumentException("Invalid canonical model id");
        if (name == null
                || name.isBlank()
                || name.length() > 120
                || name.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid model name");
        if (contextTokens < 1 || contextTokens > MAX_CONTEXT_TOKENS)
            throw new IllegalArgumentException("Invalid context length");
        if (maxCompletionTokens < 0 || maxCompletionTokens > MAX_CONTEXT_TOKENS)
            throw new IllegalArgumentException("Invalid reply ceiling");
        this.provider = provider;
        this.id = id;
        this.canonicalId = canonicalId;
        this.name = name.trim();
        this.promptUsdPerMillion =
                promptUsdPerMillion == null ? null : promptUsdPerMillion.stripTrailingZeros();
        this.completionUsdPerMillion =
                completionUsdPerMillion == null
                        ? null
                        : completionUsdPerMillion.stripTrailingZeros();
        this.contextTokens = contextTokens;
        this.reasoning = reasoning;
        this.readsImages = readsImages;
        this.maxCompletionTokens = maxCompletionTokens;
    }

    private static boolean validPrice(BigDecimal price) {
        return price != null
                && price.signum() >= 0
                && price.compareTo(MAX_PRICE) <= 0
                && price.stripTrailingZeros().scale() <= 12;
    }

    /**
     * OpenRouter answers with the requested id or its listed versioned name, nothing else. A model
     * server may also answer with a dated variant, the id followed by "-" and more.
     */
    boolean acceptsReplyModel(String reply) {
        return id.equals(reply)
                || canonicalId.equals(reply)
                || !provider.isOpenRouter() && reply != null && reply.startsWith(id + "-");
    }

    /** Providers this model is pinned to; empty lets OpenRouter route freely. */
    List<String> providers() {
        return id.equals(ModelClient.MODEL) ? ModelClient.PROVIDERS : List.of();
    }

    /** True for an OpenRouter model listed at no price. */
    public boolean isFree() {
        return provider.isOpenRouter()
                && promptUsdPerMillion.signum() == 0
                && completionUsdPerMillion.signum() == 0;
    }

    /** The maker before "Maker: " in the listed name, or empty when the name has none. */
    public String maker() {
        int colon = name.indexOf(": ");
        return colon > 0 && colon + 2 < name.length() ? name.substring(0, colon) : "";
    }

    /**
     * The listed name without its "Maker: " prefix, and without a " (free)" suffix on a free model,
     * since the price already says so.
     */
    public String shortName() {
        String maker = maker();
        String shown = maker.isEmpty() ? name : name.substring(maker.length() + 2).trim();
        String free = " (free)";
        boolean marked =
                shown.length() > free.length()
                        && shown.regionMatches(
                                true, shown.length() - free.length(), free, 0, free.length());
        return isFree() && marked
                ? shown.substring(0, shown.length() - free.length()).trim()
                : shown;
    }

    /**
     * The short name with " (free)" after it on a free model, so it never reads like a paid model
     * listed under the same name.
     */
    public String nameMarkedFree() {
        return isFree() ? shortName() + " (free)" : shortName();
    }

    /**
     * What one typical app run would cost at the listed price, in US dollars; orders OpenRouter's
     * list.
     */
    public BigDecimal appCostUsd() {
        return promptUsdPerMillion
                .multiply(BigDecimal.valueOf(TYPICAL_PROMPT_TOKENS))
                .add(
                        completionUsdPerMillion.multiply(
                                BigDecimal.valueOf(TYPICAL_COMPLETION_TOKENS)))
                .divide(MILLION);
    }

    /**
     * The listed price, for example "$0.30 in, $1.20 out per 1M tokens", "free", or empty at a
     * model server.
     */
    public String priceLabel() {
        if (!provider.isOpenRouter()) return "";
        return isFree()
                ? "free"
                : dollars(promptUsdPerMillion)
                        + " in, "
                        + dollars(completionUsdPerMillion)
                        + " out per 1M tokens";
    }

    /** Cents from a dollar up; two significant figures below, so tiny prices stay visible. */
    private static String dollars(BigDecimal usd) {
        BigDecimal shown =
                usd.compareTo(BigDecimal.ONE) < 0
                        ? usd.round(new MathContext(2, RoundingMode.HALF_UP))
                        : usd.setScale(2, RoundingMode.HALF_UP);
        int scale = Math.max(2, shown.stripTrailingZeros().scale());
        return "$" + shown.setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    public boolean matches(String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        return needle.isEmpty()
                || name.toLowerCase(Locale.ROOT).contains(needle)
                || id.toLowerCase(Locale.ROOT).contains(needle);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ModelChoice
                && ((ModelChoice) other).id.equals(id)
                && ((ModelChoice) other).provider.equals(provider);
    }

    @Override
    public int hashCode() {
        return id.hashCode() * 31 + provider.hashCode();
    }
}
