package og_spipes.service;

import og_spipes.model.spipes.ExecutionDTO;
import og_spipes.model.spipes.TransformationDTO;
import og_spipes.persistence.dao.ScriptDAO;
import og_spipes.persistence.dao.TransformationDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SPipesExecutionService {

    private static final Logger LOG = LoggerFactory.getLogger(SPipesExecutionService.class);

    private final String engineUrl;

    private final String pConfigURL;

    private final RestTemplate restTemplate;

    private final TransformationDAO transformationDAO;

    private final ScriptDAO scriptDAO;

    @Autowired
    public SPipesExecutionService(
            @Value("${engineurl}") String engineUrl,
            @Value("${rdf4j.pConfigURL}") String pConfigURL,
            RestTemplate restTemplate,
            TransformationDAO transformationDAO,
            ScriptDAO scriptDAO
    ) {
        this.engineUrl = engineUrl;
        this.pConfigURL = pConfigURL;
        this.restTemplate = restTemplate;
        this.transformationDAO = transformationDAO;
        this.scriptDAO = scriptDAO;
    }

    public String serviceExecution (
            String functionId,
            Map<String, String> params
    ) {
        String serviceUrl = engineUrl + "/service";
        params.put("id", functionId);
        params.put("_pConfigURL", pConfigURL);

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(serviceUrl);
        for (Map.Entry<String, String> pair : params.entrySet()) {
            builder.queryParam(pair.getKey(), pair.getValue());
        }

        String response = restTemplate.getForEntity(builder.build().toString(), String.class).getBody();
        LOG.info(response);
        return response;
    }

    public List<ExecutionDTO> getAllExecution() {
        return transformationDAO.getAllExecutionTransformation().stream().map(x -> {
            Map<String, Set<Object>> properties = x.getProperties();
            String pipelineURI = properties.get("http://onto.fel.cvut.cz/ontologies/s-pipes/has-pipeline-name").stream().findFirst().orElse("").toString();
            String name = pipelineURI.substring(pipelineURI.lastIndexOf("/")+1);
            String duration = properties.get("http://onto.fel.cvut.cz/ontologies/s-pipes/has-pipeline-execution-duration").stream().findFirst().orElse("").toString();
            Date startDate = (Date) properties.get("http://onto.fel.cvut.cz/ontologies/s-pipes/has-pipeline-execution-start-date").stream().findFirst().orElse(new Date());
            Date finishDate = (Date) properties.get("http://onto.fel.cvut.cz/ontologies/s-pipes/has-pipeline-execution-finish-date").stream().findFirst().orElse(new Date());
            try {
                return new ExecutionDTO(
                        pipelineURI,
                        name,
                        scriptDAO.findScriptByOntologyName(pipelineURI).getAbsolutePath(),
                        Long.parseLong(duration),
                        startDate,
                        finishDate,
                        x.getId()
                );
            } catch (IOException e) {
                LOG.warn(e.getMessage());
                return null;
            }
        }).collect(Collectors.toList());
    }

    public void deleteExecution(String transformationURI) {
        LOG.info("Remove transformation with URI: " + transformationURI);
        TransformationDTO transformationDTO = transformationDAO.find(URI.create(transformationURI));
        LOG.info("TransformationDTO: " + transformationDTO.getId());
        transformationDAO.remove(transformationDTO);
    }

}