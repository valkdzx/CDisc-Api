package dev.valkdz.cdiscapi.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArgsTest {

    private static List<String> split(String line) {
        return Args.tokenize(line.split(" "));
    }

    @Test
    void plainWordsStaySeparate() {
        assertEquals(List.of("playsound", "local:intro.mp3", "Steve"), split("playsound local:intro.mp3 Steve"));
    }

    @Test
    void quotesJoinWords() {
        assertEquals(List.of("playsound", "yt:never gonna give", "@a", "volume=50"),
                split("playsound \"yt:never gonna give\" @a volume=50"));
    }

    @Test
    void quotedOptionValue() {
        assertEquals(List.of("playsound", "local:a.mp3", "title=My Song"),
                split("playsound local:a.mp3 title=\"My Song\""));
    }

    @Test
    void urlWithEqualsIsKept() {
        assertEquals(List.of("playsound", "https://youtu.be/x?si=abc"), split("playsound https://youtu.be/x?si=abc"));
    }

    @Test
    void selectorOptionStaysWhole() {
        assertEquals(List.of("playsound", "local:a.mp3", "@e[type=pig,limit=1]", "target=@a[distance=..20,tag=x]"),
                split("playsound local:a.mp3 @e[type=pig,limit=1] target=@a[distance=..20,tag=x]"));
    }

    @Test
    void unclosedQuoteTakesTheRest() {
        assertEquals(List.of("playsound", "yt:a b"), split("playsound \"yt:a b"));
    }

    @Test
    void emptyQuotesGiveEmptyToken() {
        assertEquals(List.of("x", ""), split("x \"\""));
    }
}
