package alien4cloud.component;

import javax.annotation.Resource;

import alien4cloud.dao.IGenericSearchDAO;
import alien4cloud.model.git.CsarGitCheckoutLocation;
import alien4cloud.model.git.CsarGitRepository;
import alien4cloud.model.orchestrators.locations.LocationResourceTemplate;
import alien4cloud.tosca.parser.ParsingResult;
import alien4cloud.utils.FileUtil;
import org.alien4cloud.tosca.model.Csar;
import org.elasticsearch.index.query.QueryBuilders;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import alien4cloud.csar.services.CsarGitRepositoryService;
import alien4cloud.csar.services.CsarGitService;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:application-context-test.xml")
@Slf4j
public class CsarGitServiceTest {

    @Resource(name = "alien-es-dao")
    private IGenericSearchDAO alienDAO;
    @Resource
    CsarGitService csarGitService;
    @Resource
    CsarGitRepositoryService csarGitRepositoryService;
    @Value("${directories.alien}/${directories.upload_temp}")
    private String alienRepoDir;

    @Before
    public void cleanup() {
        alienDAO.delete(CsarGitRepository.class, QueryBuilders.matchAllQuery());
        alienDAO.delete(Csar.class, QueryBuilders.matchAllQuery());
        if (Files.isDirectory(Paths.get(alienRepoDir))) {
            log.debug("cleaning the test env");
            try {
                FileUtil.delete(Paths.get(alienRepoDir));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void importOneBranchFromGit() {
        CsarGitCheckoutLocation alien12Location = new CsarGitCheckoutLocation();
        alien12Location.setBranchId("1.2.0");
        List<CsarGitCheckoutLocation> importLocations = new LinkedList<>();
        importLocations.add(alien12Location);
        String repoId = csarGitRepositoryService.create("https://github.com/alien4cloud/tosca-normative-types.git", "", "", importLocations, false);

        List<ParsingResult<Csar>> result = csarGitService.importFromGitRepository(repoId);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("tosca-normative-types", result.get(0).getResult().getName());
    }

    @Test
    public void importManyBranchFromGit() {
        CsarGitCheckoutLocation alien12Location = new CsarGitCheckoutLocation();
        alien12Location.setBranchId("1.2.0");
        CsarGitCheckoutLocation masterLocation = new CsarGitCheckoutLocation();
        masterLocation.setBranchId("master");
        List<CsarGitCheckoutLocation> importLocations = new LinkedList<>();
        importLocations.add(alien12Location);
        importLocations.add(masterLocation);
        String repoId = csarGitRepositoryService.create("https://github.com/alien4cloud/tosca-normative-types.git", "", "", importLocations, false);

        List<ParsingResult<Csar>> result = csarGitService.importFromGitRepository(repoId);
        Assert.assertEquals(2, result.size());
        Assert.assertEquals("tosca-normative-types", result.get(0).getResult().getName());
        Assert.assertEquals("1.0.0-ALIEN12", result.get(0).getResult().getVersion());
        Assert.assertEquals("tosca-normative-types", result.get(1).getResult().getName());
        Assert.assertEquals("1.0.0-SNAPSHOT", result.get(1).getResult().getVersion());
    }

    @Test
    public void importManyBranchFromGitAndStoreLocally() {
        CsarGitCheckoutLocation alien12Location = new CsarGitCheckoutLocation();
        alien12Location.setBranchId("1.2.0");
        CsarGitCheckoutLocation masterLocation = new CsarGitCheckoutLocation();
        masterLocation.setBranchId("master");
        List<CsarGitCheckoutLocation> importLocations = new LinkedList<>();
        importLocations.add(alien12Location);
        importLocations.add(masterLocation);
        String repoId = csarGitRepositoryService.create("https://github.com/alien4cloud/tosca-normative-types.git", "", "", importLocations, true);

        List<ParsingResult<Csar>> result = csarGitService.importFromGitRepository(repoId);
        Assert.assertEquals(2, result.size());
        Assert.assertEquals("tosca-normative-types", result.get(0).getResult().getName());
        Assert.assertEquals("1.0.0-ALIEN12", result.get(0).getResult().getVersion());
        Assert.assertEquals("tosca-normative-types", result.get(1).getResult().getName());
        Assert.assertEquals("1.0.0-SNAPSHOT", result.get(1).getResult().getVersion());

        // now we re-import
        result = csarGitService.importFromGitRepository(repoId);
        Assert.assertEquals(0, result.size());
    }

}
