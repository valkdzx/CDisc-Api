package dev.valkdz.cdiscapi.command;

import java.util.ArrayList;
import java.util.List;

final class Args {

    private Args() {
    }

    static List<String> tokenize(String[] args) {
        List<String> out = new ArrayList<>();
        StringBuilder current = null;
        boolean quoted = false;

        for (String arg : args) {
            for (int i = 0; i < arg.length(); i++) {
                char c = arg.charAt(i);
                if (c == '"') {
                    quoted = !quoted;
                    if (current == null) current = new StringBuilder();
                    continue;
                }
                if (current == null) current = new StringBuilder();
                current.append(c);
            }
            if (quoted) {
                if (current == null) current = new StringBuilder();
                current.append(' ');
                continue;
            }
            if (current != null) {
                out.add(current.toString());
                current = null;
            }
        }
        if (current != null) {
            String rest = current.toString();
            out.add(quoted ? rest.stripTrailing() : rest);
        }
        return out;
    }

    static String join(List<String> tokens, int from) {
        return from >= tokens.size() ? "" : String.join(" ", tokens.subList(from, tokens.size()));
    }
}
