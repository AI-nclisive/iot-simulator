package com.ainclusive.iotsim.api.manualschema;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ainclusive.iotsim.api.error.GlobalExceptionHandler;
import com.ainclusive.iotsim.domain.manualschema.ManualSchemaService;
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

/** HTTP validation for the NodeSet import boundary. */
@WebMvcTest(
        value = ManualSchemaController.class,
        excludeAutoConfiguration = {
            SecurityAutoConfiguration.class,
            SecurityFilterAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class,
        })
@Import(GlobalExceptionHandler.class)
class ManualSchemaControllerMvcTest {
    private static final String IMPORT = "/api/v1/projects/p1/manual-schemas/import-nodeset";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ManualSchemaService manualSchemas;

    @Test
    void importRejectsMissingXmlWithBadRequest() throws Exception {
        mvc.perform(post(IMPORT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Imported\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("xml is required"));
    }

    @Test
    void importRejectsBlankXmlWithBadRequest() throws Exception {
        mvc.perform(post(IMPORT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Imported\",\"xml\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("xml is required"));
    }
}
