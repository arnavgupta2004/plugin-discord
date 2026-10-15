package io.kestra.plugin.discord;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.serializers.JacksonMapper;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;

/**
 * Renders discord-template.peb directly, to check the guards that keep Discord from rejecting the
 * whole message: empty field values are not allowed, and field names and values have length limits.
 */
@KestraTest
class DiscordTemplateFieldsTest {
    @Inject
    private VariableRenderer variableRenderer;

    @Test
    void emptyCustomMessage_isNotSentAsAnEmptyField() throws Exception {
        var embed = renderDefaultEmbed(Map.of("customMessage", ""));

        assertThat(embed, not(hasKey("fields")));
    }

    @Test
    void longCustomMessageAndFields_areShortenedToDiscordLimits() throws Exception {
        var longName = "n".repeat(300);
        var embed = renderDefaultEmbed(Map.of(
            "customMessage", "m".repeat(2000),
            "customFields", Map.of(longName, "v".repeat(2000))
        ));

        var fields = fields(embed);
        assertThat(fields.size(), is(2));
        assertThat(((String) fields.get(0).get("value")).length(), lessThanOrEqualTo(1024));
        assertThat(((String) fields.get(1).get("name")).length(), lessThanOrEqualTo(256));
        assertThat(((String) fields.get(1).get("value")).length(), lessThanOrEqualTo(1024));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> renderDefaultEmbed(Map<String, Object> custom) throws Exception {
        var template = new String(
            Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("discord-template.peb")).readAllBytes(),
            StandardCharsets.UTF_8
        );
        Map<String, Object> variables = new HashMap<>(Map.of(
            "execution", Map.of("id", "exec-id", "namespace", "company.team", "flowId", "flow", "state", Map.of("current", "FAILED")),
            "lastTask", Map.of("taskId", "failed"),
            "firstFailed", Map.of("taskId", "failed"),
            "duration", "PT1S"
        ));
        variables.putAll(custom);

        // DiscordTemplate parses the rendered template the same way
        Map<String, Object> body = JacksonMapper.ofJson().readValue(variableRenderer.render(template, variables), Map.class);
        return ((List<Map<String, Object>>) body.get("embeds")).getFirst();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> fields(Map<String, Object> embed) {
        return (List<Map<String, Object>>) embed.get("fields");
    }
}
