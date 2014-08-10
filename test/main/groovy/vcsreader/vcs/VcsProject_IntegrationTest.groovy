package vcsreader.vcs

import org.junit.Before
import org.junit.Test
import vcsreader.Change
import vcsreader.CommandExecutor
import vcsreader.Commit
import vcsreader.VcsProject

import static vcsreader.Change.Type.NEW
import static vcsreader.Change.noRevision
import static vcsreader.lang.DateTimeUtil.date
import static vcsreader.lang.DateTimeUtil.dateTime
import static vcsreader.vcs.GitShellCommands_IntegrationTest.getFirstRevision
import static vcsreader.vcs.GitShellCommands_IntegrationTest.getPrePreparedProject
import static vcsreader.vcs.GitShellCommands_IntegrationTest.getProjectFolder

class VcsProject_IntegrationTest {
    @Test void "initialize git project"() {
        def vcsRoots = [new GitVcsRoot(projectFolder, prePreparedProject, GitSettings.defaults())]
        def project = new VcsProject(vcsRoots, new CommandExecutor())

        def initResult = project.init().awaitCompletion()
        assert initResult.errors() == []
        assert initResult.isSuccessful()
    }

    @Test void "log project history single commit"() {
        def vcsRoots = [new GitVcsRoot(projectFolder, prePreparedProject, GitSettings.defaults())]
        def project = new VcsProject(vcsRoots, new CommandExecutor())
        project.init().awaitCompletion()

        def logResult = project.log(date("01/01/2013"), date("11/08/2015")).awaitCompletion()
        assert logResult.errors() == []
        assert logResult.isSuccessful()

        assert logResult.commits == [
                new Commit(
                        firstRevision,
                        dateTime("14:00:00 10/08/2014"),
                        "Dmitry Kandalov",
                        "initial commit",
                        [
                            new Change(NEW, "file1.txt", firstRevision, noRevision)
                        ]
                )
        ]
    }

    @Before void setup() {
        new File(projectFolder).deleteDir()
        new File(projectFolder).mkdirs()
    }
}
