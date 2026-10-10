package com.aidebugger;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Universal response parser for AI chat output in Minecraft.
 * Parses and decorates:
 * - In-game slash commands with interactive [Run] and [Copy] click operations.
 * - Terminal commands with [Copy] click operations.
 * - Web URLs with [Open] in browser and [Copy] click operations.
 * - Markdown bold, code, and links.
 */
public class ResponseParser {

    private static final String REROLL_CMD = "/reroll(?:\\s+(?:setup|config(?:\\s+toggle\\s+[a-zA-Z0-9_]+)?|clear|find\\s+[a-zA-Z0-9_]+|ai|debug))?";
    private static final String GAMERULE_CMD = "/gamerule\\s+[a-zA-Z0-9_]+(?:\\s+(?:true|false|\\d+))?";
    private static final String TIME_WEATHER_CMD = "/(?:time\\s+set\\s+[a-zA-Z0-9_]+|weather\\s+[a-zA-Z0-9_]+)";
    private static final String OTHER_VANILLA_CMD = "/(?:gamemode\\s+(?:survival|creative|adventure|spectator)|difficulty\\s+[a-zA-Z0-9_]+|kill(?:\\s+@[a-zA-Z0-9_]+)?|clear(?:\\s+@[a-zA-Z0-9_]+)?)";
    private static final String GENERAL_CMD = "/[a-zA-Z0-9_]+(?:\\s+(?:@[a-zA-Z0-9_]+|~?[\\-\\d]+(?:\\.[0-9]+)?|[a-zA-Z0-9_]+:[a-zA-Z0-9_]+|true|false))*(?:\\s+(?!(?:to|in|at|on|for|with|and|or|by|so|if|then|but|the|a|an|is|are|was|were|you|can|will|it)\\b)[a-zA-Z0-9_\\-\\.:]+){0,2}";

    private static final String QUOTED_CMD = "('(?<sqCmd>/[^'\r\n]+)'|\"(?<dqCmd>/[^\"\r\n]+)\"|`(?<btCmd>/[^`\r\n]+)`)";
    private static final String QUOTED_TERM = "('(?<sqTerm>ollama run [^'\r\n]+)'|\"(?<dqTerm>ollama run [^\"\r\n]+)\"|`(?<btTerm>ollama run [^`\r\n]+)`)";

    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "(?<mdLink>\\[(?<mdTitle>.*?)\\]\\((?<mdUrl>https?://[^\\s\\)]+)\\))" +
            "|(?<rawUrl>https?://[^\\s<>'\"\\)\\]]+)" +
            "|" + QUOTED_CMD +
            "|" + QUOTED_TERM +
            "|(?<rawTerm>(?<=^|\\s)(?:ollama run\\s+[a-zA-Z0-9_\\-\\.:]+)(?=[\\s.,!?:;\"')]|$))" +
            "|(?<rawCmd>(?<=^|\\s)(?:" + REROLL_CMD + "|" + GAMERULE_CMD + "|" + TIME_WEATHER_CMD + "|" + OTHER_VANILLA_CMD + "|" + GENERAL_CMD + ")(?=[\\s.,!?:;\"')]|$))" +
            "|(?<bold>\\*\\*(?<boldBody>.*?)\\*\\*)" +
            "|(?<inlineCode>`(?<inlineCodeBody>[^`]+)`)"
    );

    /**
     * Parses the LLM's raw text response into an interactive, richly-formatted Minecraft chat component.
     */
    public static MutableComponent parse(String text) {
        if (text == null || text.isBlank()) {
            return Component.empty();
        }

        MutableComponent root = Component.empty();
        Matcher matcher = TOKEN_PATTERN.matcher(text);
        int lastIdx = 0;

        while (matcher.find()) {
            if (matcher.start() > lastIdx) {
                root.append(Component.literal(text.substring(lastIdx, matcher.start())).withStyle(ChatFormatting.YELLOW));
            }

            String quotedCmd = matcher.group("sqCmd") != null ? matcher.group("sqCmd")
                    : (matcher.group("dqCmd") != null ? matcher.group("dqCmd") : matcher.group("btCmd"));
            String quotedTerm = matcher.group("sqTerm") != null ? matcher.group("sqTerm")
                    : (matcher.group("dqTerm") != null ? matcher.group("dqTerm") : matcher.group("btTerm"));

            if (matcher.group("mdLink") != null) {
                String title = matcher.group("mdTitle");
                String url = matcher.group("mdUrl");
                root.append(createUrlComponent(title, url));
            } else if (matcher.group("rawUrl") != null) {
                root.append(createUrlComponent(null, matcher.group("rawUrl")));
            } else if (quotedCmd != null) {
                root.append(createInGameCommandComponent(quotedCmd));
            } else if (quotedTerm != null) {
                root.append(createTerminalCommandComponent(quotedTerm));
            } else if (matcher.group("rawTerm") != null) {
                root.append(createTerminalCommandComponent(matcher.group("rawTerm")));
            } else if (matcher.group("rawCmd") != null) {
                String cmd = matcher.group("rawCmd");
                if (cmd != null && cmd.startsWith("/") && cmd.length() > 1 && !cmd.contains("//")) {
                    root.append(createInGameCommandComponent(cmd));
                } else {
                    root.append(Component.literal(cmd != null ? cmd : "").withStyle(ChatFormatting.YELLOW));
                }
            } else if (matcher.group("bold") != null) {
                root.append(Component.literal(matcher.group("boldBody")).withStyle(ChatFormatting.YELLOW));
            } else if (matcher.group("inlineCode") != null) {
                String code = matcher.group("inlineCodeBody");
                if (code.startsWith("/")) {
                    root.append(createInGameCommandComponent(code));
                } else if (code.startsWith("ollama run")) {
                    root.append(createTerminalCommandComponent(code));
                } else {
                    root.append(Component.literal(code).withStyle(ChatFormatting.AQUA));
                }
            }

            lastIdx = matcher.end();
        }

        if (lastIdx < text.length()) {
            root.append(Component.literal(text.substring(lastIdx)).withStyle(ChatFormatting.YELLOW));
        }

        return root;
    }

    /**
     * Creates an in-game command text component with [Run] and [Copy] action badges.
     */
    public static MutableComponent createInGameCommandComponent(String rawCommand) {
        String clean = rawCommand.trim();
        // Remove enclosing quotes or backticks if present
        if ((clean.startsWith("'") && clean.endsWith("'")) ||
            (clean.startsWith("\"") && clean.endsWith("\"")) ||
            (clean.startsWith("`") && clean.endsWith("`"))) {
            clean = clean.substring(1, clean.length() - 1).trim();
        }
        // Remove trailing punctuation accidentally captured
        while (clean.endsWith(".") || clean.endsWith(",") || clean.endsWith("!")) {
            clean = clean.substring(0, clean.length() - 1).trim();
        }

        if (!clean.startsWith("/")) {
            clean = "/" + clean;
        }
        final String cmd = clean;

        MutableComponent root = Component.empty();

        // Main command: clickable to run
        root.append(Component.literal(cmd).withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.RunCommand(cmd))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to run command: " + cmd)))));

        // Action badge: [Run]
        root.append(Component.literal(" "));
        root.append(Component.literal("[Run]").withStyle(style -> style
                .withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent.RunCommand(cmd))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to execute: " + cmd)))));

        // Action badge: [Copy]
        root.append(Component.literal(" "));
        root.append(Component.literal("[Copy]").withStyle(style -> style
                .withColor(ChatFormatting.GOLD)
                .withClickEvent(new ClickEvent.CopyToClipboard(cmd))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to copy: " + cmd)))));

        return root;
    }

    /**
     * Creates a terminal command text component with a [Copy] action badge.
     */
    public static MutableComponent createTerminalCommandComponent(String rawCommand) {
        String clean = rawCommand.trim();
        if ((clean.startsWith("'") && clean.endsWith("'")) ||
            (clean.startsWith("\"") && clean.endsWith("\"")) ||
            (clean.startsWith("`") && clean.endsWith("`"))) {
            clean = clean.substring(1, clean.length() - 1).trim();
        }
        while (clean.endsWith(".") || clean.endsWith(",") || clean.endsWith("!")) {
            clean = clean.substring(0, clean.length() - 1).trim();
        }
        final String cmd = clean;

        MutableComponent root = Component.empty();

        // Main command: clickable to copy
        root.append(Component.literal(cmd).withStyle(style -> style
                .withColor(ChatFormatting.GREEN)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.CopyToClipboard(cmd))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to copy terminal command: " + cmd)))));

        // Action badge: [Copy]
        root.append(Component.literal(" "));
        root.append(Component.literal("[Copy]").withStyle(style -> style
                .withColor(ChatFormatting.GOLD)
                .withClickEvent(new ClickEvent.CopyToClipboard(cmd))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to copy to clipboard!")))));

        return root;
    }

    /**
     * Creates a web URL text component with [Open] and [Copy] action badges.
     */
    public static MutableComponent createUrlComponent(String label, String rawUrl) {
        String clean = rawUrl.trim();
        while (clean.endsWith(".") || clean.endsWith(",") || clean.endsWith("!")) {
            clean = clean.substring(0, clean.length() - 1).trim();
        }
        final String url = clean;
        final String display = (label != null && !label.isBlank()) ? label.trim() : url;

        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception e) {
            uri = null;
        }
        final URI finalUri = uri;

        MutableComponent root = Component.empty();

        // Link text
        root.append(Component.literal(display).withStyle(style -> {
            var s = style.withColor(ChatFormatting.AQUA).withUnderlined(true)
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to open link: " + url)));
            if (finalUri != null) {
                s = s.withClickEvent(new ClickEvent.OpenUrl(finalUri));
            }
            return s;
        }));

        // Action badge: [Open]
        root.append(Component.literal(" "));
        root.append(Component.literal("[Open]").withStyle(style -> {
            var s = style.withColor(ChatFormatting.AQUA)
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to open in browser: " + url)));
            if (finalUri != null) {
                s = s.withClickEvent(new ClickEvent.OpenUrl(finalUri));
            }
            return s;
        }));

        // Action badge: [Copy]
        root.append(Component.literal(" "));
        root.append(Component.literal("[Copy]").withStyle(style -> style
                .withColor(ChatFormatting.GRAY)
                .withClickEvent(new ClickEvent.CopyToClipboard(url))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to copy link: " + url)))));

        return root;
    }
}
