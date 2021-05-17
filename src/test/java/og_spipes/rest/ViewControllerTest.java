package og_spipes.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.cvut.kbss.jsonld.jackson.JsonLdModule;
import og_spipes.model.view.View;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.*;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class ViewControllerTest {

    @Value("${scriptPaths}")
    private String scriptPaths;

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper mapper = new ObjectMapper();
//    private static Repository sesameRepo;
//
//    @BeforeAll
//    public static void beforeAll() throws IOException, InterruptedException {
//        File dataDir = new File("file:/tmp/og_spipes_sesame/repositories/s-pipes-hello-world");
//        sesameRepo = new SailRepository( new NativeStore(dataDir) );
//        sesameRepo.initialize();
//        RepositoryConnection conn = sesameRepo.getConnection();
//        conn.add(new File("/home/jordan/IdeaProjects/s-pipes-newgen/src/test/resources/rdf4j_source/repositories/rdf4j_export"), null, RDFFormat.TURTLE);
//    }

    @BeforeEach
    public void init() throws Exception {
        File scriptsHomeTmp = new File(scriptPaths);
        if (scriptsHomeTmp.exists()) {
            FileSystemUtils.deleteRecursively(scriptsHomeTmp);
            Files.createDirectory(Paths.get(scriptsHomeTmp.toURI()));
        }
        FileUtils.copyDirectory(new File("src/test/resources/scripts_test/sample/"), scriptsHomeTmp);
        mapper.registerModule(new JsonLdModule());
    }

    @Test
    @DisplayName("Get graph view")
    public void testViewOfScript() throws Exception {
        File scriptPath = new File(scriptPaths + "/hello-world/hello-world2.sms.ttl");
        MvcResult mvcResult = this.mockMvc.perform(post("/views/new")
                .content(
                        "{" +
                                "\"@type\":\"http://onto.fel.cvut.cz/ontologies/s-pipes/script-dto\"," +
                                "\"http://onto.fel.cvut.cz/ontologies/s-pipes/has-script-path\":\"" + scriptPath + "\"" +
                                "}"
                )
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();

        String content = mvcResult.getResponse().getContentAsString();

        View view = mapper.readValue(content, View.class);
        //TODO not working with shared test SpringBoot context
        Assertions.assertEquals(4, view.getNodes().size());
    }

    @Test
    @DisplayName("Get graph view of complicated script")
    public void testViewOfComplicatedScript() throws Exception {
        File scriptPath = new File(scriptPaths + "/vfn-example/vfn-form-modules.ttl");
        MvcResult mvcResult = this.mockMvc.perform(post("/views/new")
                .content(
                        "{" +
                                "\"@type\":\"http://onto.fel.cvut.cz/ontologies/s-pipes/script-dto\"," +
                                "\"http://onto.fel.cvut.cz/ontologies/s-pipes/has-script-path\":\"" + scriptPath + "\"" +
                                "}"
                )
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();

        String content = mvcResult.getResponse().getContentAsString();

        View view = mapper.readValue(content, View.class);
        Assertions.assertEquals(36, view.getNodes().size());
    }

    @Test
    @DisplayName("Get graph view with execution")
    @Disabled //TODO ask how to add data to sesame DB
    public void testViewOfScriptWithExecution() throws Exception {
        //TODO enforce some assertion
        File scriptPath = new File(scriptPaths + "/hello-world/hello-world2.sms.ttl");
        String transformationId = "http://onto.fel.cvut.cz/ontologies/dataset-descriptor/transformation/1619043854875003";
        this.mockMvc.perform(post("/views/new")
                .content(
                        "{" +
                                "\"@type\":\"http://onto.fel.cvut.cz/ontologies/s-pipes/script-dto\"," +
                                "\"http://onto.fel.cvut.cz/ontologies/s-pipes/has-script-path\":\"" + scriptPath + "\", " +
                                "\"http://onto.fel.cvut.cz/ontologies/s-pipes/has-transformation-id\":\"" + transformationId + "\"" +
                                "}"
                )
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @AfterEach
    public void after() {
        FileSystemUtils.deleteRecursively(new File(scriptPaths));
    }

}

