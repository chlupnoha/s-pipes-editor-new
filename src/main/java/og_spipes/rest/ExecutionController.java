package og_spipes.rest;

import og_spipes.model.spipes.ExecutionDTO;
import og_spipes.service.FunctionService;
import og_spipes.service.SPipesExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/execution")
public class ExecutionController {

    private static final Logger LOG = LoggerFactory.getLogger(ExecutionController.class);

    private final FunctionService functionService;

    private final SPipesExecutionService executionService;

    @Autowired
    public ExecutionController(FunctionService functionService, SPipesExecutionService executionService) {
        this.functionService = functionService;
        this.executionService = executionService;
    }

    @GetMapping(path = "/history")
    public List<ExecutionDTO> historyOfAllExecution() {
        return executionService.getAllExecution();
    }

    @PostMapping(path = "/remove")
    public void removeExecution(@RequestBody String transformation) {
        executionService.deleteExecution(transformation);
    }

    @PostMapping(path = "/history-module")
    public void historyOfModule(@RequestBody String executionId, String moduleId) {

    }

}
