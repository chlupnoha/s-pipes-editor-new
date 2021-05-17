package og_spipes.rest;

import og_spipes.service.SPipesExecutionService;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations="classpath:application.properties")
public class ExecutionControllerTest {

    @Value("${scriptPaths}")
    private String scriptPaths;

    @Autowired
    private SPipesExecutionService executionService;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    public void init() throws Exception {
        File scriptsHomeTmp = new File(scriptPaths);
        if(scriptsHomeTmp.exists()){
            FileSystemUtils.deleteRecursively(scriptsHomeTmp);
            Files.createDirectory(Paths.get(scriptsHomeTmp.toURI()));
        }
        FileUtils.copyDirectory(new File("src/test/resources/scripts_test/sample/hello-world"), scriptsHomeTmp);
    }

    @Test
    @DisplayName("List history of execution")
    public void testScriptsEndpoint() throws Exception {
        this.mockMvc.perform(get("/execution/history"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").exists());
    }

    @Test
    @DisplayName("Remove execution")
    public void testRemoveExecution() throws Exception {
        int originExecutionSize = executionService.getAllExecution().size();
        System.out.println(originExecutionSize);

        this.mockMvc.perform(post("/execution/remove")
                .content("http://onto.fel.cvut.cz/ontologies/dataset-descriptor/transformation/1620741828749000")
                .contentType(MediaType.TEXT_PLAIN))
                .andDo(print())
                .andExpect(status().isOk());

        int expectedExecutionSize = executionService.getAllExecution().size();
        Assertions.assertEquals(expectedExecutionSize, originExecutionSize-1);
    }

    @AfterEach
    public void after() {
        FileSystemUtils.deleteRecursively(new File(scriptPaths));
    }

}