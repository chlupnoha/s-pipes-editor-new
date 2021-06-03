package og_spipes.persistence;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.EntityManagerFactory;
import og_spipes.model.spipes.TestJSONLD;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.config.RepositoryConfigException;
import org.eclipse.rdf4j.repository.manager.RepositoryProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.FileSystemUtils;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class SPipesModulesDownloader {

    private static final Logger LOG = LoggerFactory.getLogger(SPipesModulesDownloader.class);

    private final String scriptPaths;

    private final String sPipesModulesGit;

    private final boolean downloadSPipesModules;

    public SPipesModulesDownloader(
            @Value("${scriptPaths}") String scriptPaths,
            @Value("${sPipesModulesGit}") String sPipesModulesGit,
            @Value("${downloadSPipesModules}") boolean downloadSPipesModules
    ) {
        this.scriptPaths = scriptPaths;
        this.sPipesModulesGit = sPipesModulesGit;
        this.downloadSPipesModules = downloadSPipesModules;
    }

    /**
     * Download newest version of s-pipes modules to scriptPaths location.
     * @throws GitAPIException
     * @throws IOException
     */
    @PostConstruct
    private void downloadNewestSPipesModules() throws GitAPIException, IOException {
        if(downloadSPipesModules){
            Path tempDirWithPrefix = Files.createTempDirectory("spipes-modules");
            Path destinationDir = Paths.get(scriptPaths + "/modules");

            if(destinationDir.toFile().exists()){
                FileSystemUtils.deleteRecursively(destinationDir);
                Files.createDirectory(destinationDir);
            }else{
                Files.createDirectory(destinationDir);
            }

            Git.cloneRepository()
                    .setURI(sPipesModulesGit)
                    .setDirectory(tempDirWithPrefix.toFile())
                    .call();

            List<Path> paths = Files.walk(tempDirWithPrefix).collect(Collectors.toList());
            for(Path p : paths){
                if (Files.isRegularFile(p) && p.getFileName().toString().endsWith(".ttl")) {
                    Path destinationFile = Paths.get(destinationDir + "/" + p.getFileName());
                    LOG.info("Copy {} module to {}", p.toAbsolutePath(), destinationFile.toFile().getAbsolutePath());
                    Files.copy(p, destinationFile, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

}