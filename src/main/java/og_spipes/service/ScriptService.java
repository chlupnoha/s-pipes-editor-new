package og_spipes.service;

import com.google.common.base.Charsets;
import com.google.common.io.CharSink;
import com.google.common.io.Files;
import og_spipes.model.Vocabulary;
import og_spipes.model.spipes.Module;
import og_spipes.model.spipes.ModuleType;
import og_spipes.persistence.dao.ScriptDAO;
import og_spipes.service.exception.FileExistsException;
import og_spipes.service.exception.MissingOntologyException;
import og_spipes.service.exception.OntologyDuplicationException;
import org.apache.jena.rdf.model.*;
import org.apache.jena.rdf.model.impl.PropertyImpl;
import org.apache.jena.rdf.model.impl.ResourceImpl;
import org.apache.jena.util.ResourceUtils;
import org.apache.jena.vocabulary.OWL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.apache.jena.util.FileUtils.langTurtle;

@Service
public class ScriptService {

    private static final Logger LOG = LoggerFactory.getLogger(OntologyHelper.class);

    private final ScriptDAO scriptDao;
    private final OntologyHelper ontologyHelper;

    @Autowired
    public ScriptService(ScriptDAO scriptDao, OntologyHelper ontologyHelper) {
        this.scriptDao = scriptDao;
        this.ontologyHelper = ontologyHelper;
    }

    public List<Module> getModules(String filepath){
        Model ontModel = ontologyHelper.createOntModel(new File(filepath));
        return scriptDao.getModules(ontModel);
    }

    public List<ModuleType> getModuleTypes(String filepath){
        Model ontModel = ontologyHelper.createOntModel(new File(filepath));
        return scriptDao.getModuleTypes(ontModel);
    }

    public void createDependency(String scriptPath, String from, String to) throws FileNotFoundException {
        Model ontModel = ontologyHelper.createOntModel(new File(scriptPath));
        List<Resource> resources = ontModel.listSubjects().toList().stream().filter(Objects::nonNull).filter(x -> x.getURI() != null).collect(Collectors.toList());
        Optional<Resource> moduleFrom = resources.stream().filter(x -> x.getURI().equals(from)).findAny();
        Optional<Resource> moduleTo = resources.stream().filter(x -> x.getURI().equals(to)).findAny();

        if(!moduleFrom.isPresent() || !moduleTo.isPresent()){
            throw new IllegalArgumentException("FROM MODULE: " + moduleFrom + " OR TO MODULE " + moduleTo + "CANT BE NULL");
        }

        ontModel.add(moduleFrom.get(), new PropertyImpl(Vocabulary.s_p_next), moduleTo.get());
        FileOutputStream os = new FileOutputStream(scriptPath);
        ontModel.write(os, langTurtle);
    }

    public void deleteDependency(String scriptPath, String from, String to) throws FileNotFoundException {
        Model ontModel = ontologyHelper.createOntModel(new File(scriptPath));
        ontModel.removeAll(
                ontModel.getResource(from),
                new PropertyImpl(Vocabulary.s_p_next),
                ontModel.getResource(to)
        );
        FileOutputStream os = new FileOutputStream(scriptPath);
        ontModel.write(os, langTurtle);
    }

    public void deleteModule(String scriptPath, String module) throws FileNotFoundException {
        Model ontModel = ontologyHelper.createOntModel(new File(scriptPath));
        List<Statement> fromStatements = OntologyHelper.getAllStatementsRecursively(ontModel, module);
        ontModel.remove(fromStatements);
        ontModel.removeAll(null, null, ontModel.getResource(module));
        FileOutputStream os = new FileOutputStream(scriptPath);
        ontModel.write(os, langTurtle);
    }

    public void deleteModuleOnly(String scriptPath, String module) throws FileNotFoundException {
        Model ontModel = ontologyHelper.createOntModel(new File(scriptPath));
        List<Statement> fromStatements = OntologyHelper.getAllStatementsRecursively(ontModel, module);
        ontModel.remove(fromStatements);
        FileOutputStream os = new FileOutputStream(scriptPath);
        ontModel.write(os, langTurtle);
    }

    public void moveModule(String scriptFrom, String scriptTo, String moduleURI, boolean renameBaseOntology) throws FileNotFoundException {
        String fromOntology = OntologyHelper.getOntologyUri(new File(scriptFrom));
        String toOntology = OntologyHelper.getOntologyUri(new File(scriptTo));

        File fromFile = new File(scriptFrom);
        Model fromModel = ModelFactory.createDefaultModel().read(fromFile.getAbsolutePath(), langTurtle);

        Resource resource = fromModel.getResource(moduleURI);
        Model model = ResourceUtils.reachableClosure(resource);

        //should be transactional
        File toFile = new File(scriptTo);
        Model toModel = ModelFactory.createDefaultModel().read(toFile.getAbsolutePath(), langTurtle);
        toModel.add(model);
        if(renameBaseOntology){
            toModel.listSubjects().forEachRemaining(s -> {
                if(s.toString().contains(fromOntology)){
                    String replaced = s.toString().replace(fromOntology, toOntology);
                    ResourceUtils.renameResource(s, replaced);
                }
            });
        }
        toModel.write(new FileOutputStream(toFile), langTurtle);
        toModel.close();

        deleteModuleOnly(scriptFrom, moduleURI);

        //rename old prefix to new one in all files
        if(renameBaseOntology){
            String renamedModuleUri = moduleURI.replace(fromOntology, toOntology);
            for(File file : scriptDao.getScripts()){
                Model resModel = ModelFactory.createDefaultModel().read(file.getAbsolutePath(), langTurtle);
                AtomicBoolean changed = new AtomicBoolean(false);
                resModel.listSubjects().toList().forEach(s -> {
                    if(!s.isAnon()){
                        String replaced = s.toString().replace(moduleURI, renamedModuleUri);
                        if(!replaced.equals(s.toString())){
                            changed.set(true);
                        }
                        ResourceUtils.renameResource(s, replaced);
                    }
                });
                resModel.listStatements().toList().forEach(s -> {
                    RDFNode rdfNode = s.getObject();
                    if(!rdfNode.isAnon() && rdfNode.toString().equals(moduleURI)){
                        changed.set(true);
                        String replaced = rdfNode.toString().replace(moduleURI, renamedModuleUri);
                        ResourceUtils.renameResource(rdfNode.asResource(), replaced);
                    }
                });
                if(changed.get()){
                    resModel.write(new FileOutputStream(file), langTurtle);
                    resModel.close();
                }
            }
        }
        //TODO resolve imports
    }

    public void createScript(String directory, String filename, URI ontologyURI) throws IOException, OntologyDuplicationException, FileExistsException {
        File template = new File("src/main/resources/template/hello-world3.sms.ttl");

        List<String> ontologyNames = scriptDao.getScripts().stream()
                .map(OntologyHelper::getOntologyUri)
                .collect(Collectors.toList());

        if(ontologyNames.contains(ontologyURI)){
            throw new OntologyDuplicationException(ontologyURI + " ontology already exists");
        }

        List<String> directoryFiles = ScriptDAO.getScripts(directory).stream().map(File::getName).collect(Collectors.toList());
        if(directoryFiles.contains(filename)){
            throw new FileExistsException(filename + " already exists");
        }

        String lines = Files.toString(template, Charsets.UTF_8).replace("ONTOLOGY_NAME", ontologyURI.toString());
        File file = new File(directory + "/" + filename);
        CharSink sink = Files.asCharSink(file, Charsets.UTF_8);
        sink.write(lines);
    }

    public void removeScriptOntology(String scriptPath, String ontology) throws FileNotFoundException {
        Model ontModel = ontologyHelper.createOntModel(new File(scriptPath));
        ontModel.removeAll(ontModel.getResource(ontology), OWL.imports, null);
        ontModel.removeAll(null, OWL.imports, ontModel.getResource(ontology));
        FileOutputStream os = new FileOutputStream(scriptPath);
        ontModel.write(os, langTurtle);
    }

    public void addScriptOntology(String scriptPath, String ontologyName) throws MissingOntologyException, FileNotFoundException {
        File f = new File(scriptPath);
        Model defaultModel = ontologyHelper.createOntModel(f);
        List<Statement> statements = defaultModel
                .read(f.getAbsolutePath(), langTurtle)
                .listStatements(null, OWL.imports, (RDFNode) null).toList();

        if(statements.size() == 0){
            throw new MissingOntologyException("Script does not contain ontology.");
        }

        Model resModel = ontologyHelper.createOntModel(f);
        Statement ontology = statements.get(0);
        resModel.add(ontology.getSubject(), OWL.imports, new ResourceImpl(ontologyName));
        FileOutputStream os = new FileOutputStream(scriptPath);
        resModel.write(os, langTurtle);
    }

    public List<String> getScriptImportedOntologies(String scriptPath) {
        File f = new File(scriptPath);
        Model defaultModel = ModelFactory.createDefaultModel();
        List<Statement> statements = defaultModel
                .read(f.getAbsolutePath(), langTurtle)
                .listStatements(null, OWL.imports, (RDFNode) null).toList();

        return statements.stream().map(x -> x.getObject().toString())
                .filter(x -> !x.equals("http://onto.fel.cvut.cz/ontologies/s-pipes-lib"))
                .collect(Collectors.toList());
     }

}
