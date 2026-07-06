package com.ainclusive.iotsim.api.scenario;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ainclusive.iotsim.api.error.GlobalExceptionHandler;
import com.ainclusive.iotsim.domain.common.ConcurrencyConflictException;
import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.common.ScenarioInvalidException;
import com.ainclusive.iotsim.domain.scenario.Scenario;
import com.ainclusive.iotsim.domain.scenario.ScenarioLiveRunService;
import com.ainclusive.iotsim.domain.scenario.ScenarioLiveRunSummary;
import com.ainclusive.iotsim.domain.scenario.ScenarioService;
import com.ainclusive.iotsim.domain.scenario.ScenarioValidationService;
import com.ainclusive.iotsim.domain.support.Page;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer (MockMvc) tests for ScenarioController (IS-121).
 *
 * <p>Tests HTTP-layer concerns not exercised by the POJO unit tests:
 * status codes from GlobalExceptionHandler, ETag/Location headers, JSON content-type,
 * and RFC 9457 problem-detail responses. Security is excluded so tests focus on
 * HTTP mapping — authorization enforcement is covered separately.
 */
@WebMvcTest(
        value = ScenarioController.class,
        excludeAutoConfiguration = {
            SecurityAutoConfiguration.class,
            SecurityFilterAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class,
        })
@Import(GlobalExceptionHandler.class)
class ScenarioControllerMvcTest {

    private static final String BASE = "/api/v1/projects/p1/scenarios";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ScenarioService scenarios;

    @MockitoBean
    ScenarioValidationService validationService;

    @MockitoBean
    ScenarioLiveRunService liveRunService;

    private static Scenario sample(long version) {
        return new Scenario("scn-1", "p1", "Flow", "DRAFT", "{}",
                List.of(), Instant.EPOCH, Instant.EPOCH, "local", version);
    }

    @BeforeEach
    void stubDefaults() {
        when(scenarios.create(any(), any(), any(), anyList(), any())).thenReturn(sample(0));
        when(scenarios.get(any(), any())).thenReturn(sample(3));
        when(scenarios.update(any(), any(), any(), any(), any(), anyLong())).thenReturn(sample(4));
        when(scenarios.duplicate(any(), any(), any())).thenReturn(sample(0));
        when(scenarios.listPaged(any(), any(), any())).thenReturn(
                new Page<>(List.of(sample(1)), null, 50));
    }

    // ── Happy-path HTTP structure ─────────────────────────────────────────────

    @Test
    void getReturns200WithEtag() throws Exception {
        mvc.perform(get(BASE + "/scn-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"3\""))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value("scn-1"));
    }

    @Test
    void listReturns200WithItemsArray() throws Exception {
        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void createReturns201WithLocationAndEtag() throws Exception {
        mvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Flow\",\"steps\":[]}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/projects/p1/scenarios/scn-1"))
                .andExpect(header().string("ETag", "\"0\""));
    }

    @Test
    void patchReturns200WithBumpedEtag() throws Exception {
        mvc.perform(patch(BASE + "/scn-1")
                        .header("If-Match", "\"3\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"4\""));
    }

    @Test
    void deleteReturns204() throws Exception {
        mvc.perform(delete(BASE + "/scn-1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void duplicateReturns201WithLocation() throws Exception {
        mvc.perform(post(BASE + "/scn-1/duplicate"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"));
    }

    @Test
    void runReturns200WithRunIdAndEvidenceId() throws Exception {
        when(liveRunService.start(any(), any(), isNull(), isNull()))
                .thenReturn(new ScenarioLiveRunSummary("run-42", "ev-42"));
        mvc.perform(post(BASE + "/scn-1/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value("run-42"))
                .andExpect(jsonPath("$.evidenceId").value("ev-42"));
    }

    // ── GlobalExceptionHandler mappings ───────────────────────────────────────

    @Test
    void resourceNotFoundYields404ProblemDetail() throws Exception {
        when(scenarios.get(anyString(), anyString()))
                .thenThrow(new ResourceNotFoundException("Scenario", "scn-x"));
        mvc.perform(get(BASE + "/scn-x"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void illegalArgumentYields400ProblemDetail() throws Exception {
        mvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \",\"steps\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void concurrencyConflictYields409ProblemDetail() throws Exception {
        when(scenarios.update(any(), any(), any(), any(), any(), anyLong()))
                .thenThrow(new ConcurrencyConflictException("Scenario", "scn-1", 1));
        mvc.perform(patch(BASE + "/scn-1")
                        .header("If-Match", "\"1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void missingIfMatchYields428ProblemDetail() throws Exception {
        mvc.perform(patch(BASE + "/scn-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\"}"))
                .andExpect(status().is(428))
                .andExpect(jsonPath("$.status").value(428));
    }

    @Test
    void scenarioInvalidYields422WithIssuesList() throws Exception {
        when(liveRunService.start(any(), any(), any(), any()))
                .thenThrow(new ScenarioInvalidException("scn-1", List.of("step 0: source required")));
        mvc.perform(post(BASE + "/scn-1/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.issues").isArray());
    }
}
