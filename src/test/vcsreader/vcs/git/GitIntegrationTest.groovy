package vcsreader.vcs.git
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import vcsreader.Change
import vcsreader.Commit
import vcsreader.VcsProject

import static vcsreader.Change.Type.*
import static vcsreader.Change.noRevision
import static vcsreader.lang.DateTimeUtil.date
import static vcsreader.lang.DateTimeUtil.dateTime
import static vcsreader.vcs.TestUtil.assertEqualCommits
import static vcsreader.vcs.git.GitIntegrationTestConfig.*

class GitIntegrationTest {
	private final gitSettings = GitSettings.defaults().withGitPath(pathToGit)
    private final vcsRoots = [new GitVcsRoot(projectFolder, referenceProject, gitSettings)]
    private final project = new VcsProject(vcsRoots)

    @Test void "clone project"() {
        def cloneResult = project.cloneToLocal()
        assert cloneResult.vcsErrors().empty
        assert cloneResult.isSuccessful()
    }

    @Test void "clone project failure"() {
        def vcsRoots = [new GitVcsRoot(projectFolder, nonExistentPath, gitSettings)]
        def project = new VcsProject(vcsRoots)

        def cloneResult = project.cloneToLocal()

        assert !cloneResult.isSuccessful()
        assert cloneResult.vcsErrors().size() == 1
    }

    @Test void "update project"() {
        project.cloneToLocal()
        def updateResult = project.update()
        assert updateResult.vcsErrors().empty
        assert updateResult.isSuccessful()
    }

    @Test void "update project failure"() {
        project.cloneToLocal()
        new File(projectFolder).deleteDir()

        def updateResult = project.update()
        assert !updateResult.isSuccessful()
        assert updateResult.vcsErrors() != []
    }

    @Test void "log empty list of commits from project history"() {
        project.cloneToLocal()
        def logResult = project.log(date("01/08/2014"), date("02/08/2014"))

        assert logResult.commits().empty
        assert logResult.vcsErrors().empty
        assert logResult.isSuccessful()
    }

    @Test void "log single commit from project history"() {
        project.cloneToLocal()
        def logResult = project.log(date("10/08/2014"), date("11/08/2014"))

        assertEqualCommits(logResult, [
                new Commit(
                        revision(1), noRevision,
                        dateTime("14:00:00 10/08/2014"),
                        author,
                        "initial commit",
                        [new Change(NEW, "file1.txt", revision(1))]
                )
        ])
    }

    @Test void "log several commits from project history"() {
        project.cloneToLocal()
        def logResult = project.log(date("10/08/2014"), date("12/08/2014"))

        assertEqualCommits(logResult, [
                new Commit(
                        revision(1), noRevision,
                        dateTime("14:00:00 10/08/2014"),
                        author,
                        "initial commit",
                        [new Change(NEW, "file1.txt", revision(1))]
                ),
                new Commit(
                        revision(2), revision(1),
                        dateTime("14:00:00 11/08/2014"),
                        author,
                        "added file2, file3",
                        [
                            new Change(NEW, "file2.txt", "", revision(2), noRevision),
                            new Change(NEW, "file3.txt", "", revision(2), noRevision)
                        ]
                )
        ])
    }

    @Test void "log modification commit"() {
        project.cloneToLocal()
        def logResult = project.log(date("12/08/2014"), date("13/08/2014"))

        assertEqualCommits(logResult, [
                new Commit(
                        revision(3), revision(2),
                        dateTime("14:00:00 12/08/2014"),
                        author,
                        "modified file2, file3",
                        [
                            new Change(MODIFICATION, "file2.txt", "file2.txt", revision(3), revision(2)),
                            new Change(MODIFICATION, "file3.txt", "file3.txt", revision(3), revision(2))
                        ]
                )
        ])
    }

    @Test void "log moved file commit"() {
        project.cloneToLocal()
        def logResult = project.log(date("13/08/2014"), date("14/08/2014"))

        assertEqualCommits(logResult, [
                new Commit(
                        revision(4), revision(3),
                        dateTime("14:00:00 13/08/2014"),
                        author,
                        "moved file1",
                        [new Change(MOVED, "folder1/file1.txt", "file1.txt", revision(4), revision(3))]
                )
        ])
    }

    @Test void "log moved and renamed file commit"() {
        project.cloneToLocal()
        def logResult = project.log(date("14/08/2014"), date("15/08/2014"))

        assertEqualCommits(logResult, [
                new Commit(
                        revision(5), revision(4),
                        dateTime("14:00:00 14/08/2014"),
                        author,
                        "moved and renamed file1",
                        [new Change(MOVED, "folder2/renamed_file1.txt", "folder1/file1.txt", revision(5), revision(4))]
                )
        ])
    }

    @Test void "log deleted file"() {
        project.cloneToLocal()
        def logResult = project.log(date("15/08/2014"), date("16/08/2014"))

        assertEqualCommits(logResult, [
                new Commit(
                        revision(6), revision(5),
                        dateTime("14:00:00 15/08/2014"),
                        author,
                        "deleted file1",
                        [new Change(DELETED, "", "folder2/renamed_file1.txt", revision(6), revision(5))]
                )
        ])
    }

    @Test void "log file with spaces and quotes in its name"() {
        project.cloneToLocal()
        def logResult = project.log(date("16/08/2014"), date("17/08/2014"))

        assertEqualCommits(logResult, [
                new Commit(
                        revision(7), revision(6),
                        dateTime("14:00:00 16/08/2014"),
                        author,
                        "added file with spaces and quotes",
                        [new Change(NEW, "\"file with spaces.txt\"", "", revision(7), noRevision)]
                )
        ])
    }

    @Test void "log commit with non-ascii message and file content"() {
        project.cloneToLocal()
        def logResult = project.log(date("17/08/2014"), date("18/08/2014"))

        assertEqualCommits(logResult, [
                new Commit(
                        revision(8), revision(7),
                        dateTime("15:00:00 17/08/2014"),
                        author,
                        "non-ascii комментарий",
                        [new Change(NEW, "non-ascii.txt", "", revision(8), noRevision)]
                )
        ])
    }

    @Test void "log commit with empty message"() {
        project.cloneToLocal()
        def logResult = project.log(date("18/08/2014"), date("19/08/2014"))

        assertEqualCommits(logResult, [
                new Commit(
                        revision(9), revision(8),
                        dateTime("16:00:00 18/08/2014"),
                        author,
                        "",
                        [new Change(NEW, "file4.txt", "", revision(9), noRevision)]
                )
        ])
    }

    @Test void "log commit with no changes"() {
        project.cloneToLocal()
        def logResult = project.log(date("19/08/2014"), date("20/08/2014"))

        assertEqualCommits(logResult, [
                new Commit(
                        revision(10), revision(9),
                        dateTime("17:00:00 19/08/2014"),
                        author,
                        "commit with no changes",
                        []
                )
        ])
    }

    @Test void "log content of modified file"() {
        project.cloneToLocal()
        def logResult = project.log(date("12/08/2014"), date("13/08/2014"))

        def change = logResult.commits().first().changes.first()
        assert change.type == MODIFICATION
        assert change.content().text == "file2 new content"
        assert change.contentBefore().text == "file2 content"
        assert logResult.vcsErrors().empty
        assert logResult.isSuccessful()
    }

    @Test void "log content of new file"() {
        project.cloneToLocal()
        def logResult = project.log(date("11/08/2014"), date("12/08/2014"))

        def change = logResult.commits().first().changes.first()
        assert change.type == NEW
        assert change.content().text == "file2 content"
        assert change.contentBefore() == Change.Content.none
        assert logResult.vcsErrors().empty
        assert logResult.isSuccessful()
    }

    @Test void "log content of deleted file"() {
        project.cloneToLocal()
        def logResult = project.log(date("15/08/2014"), date("16/08/2014"))

        def change = logResult.commits().first().changes.first()
        assert change.type == DELETED
        assert change.content() == Change.Content.none
        assert change.contentBefore().text == "file1 content"
        assert logResult.vcsErrors().empty
        assert logResult.isSuccessful()
    }

    @Before void setup() {
        new File(projectFolder).deleteDir()
        new File(projectFolder).mkdirs()
    }

    @BeforeClass static void setupConfig() {
        initTestConfig()
    }

    private static final String projectFolder = "/tmp/git-commands-test/git-repo-${GitIntegrationTest.simpleName}/"
}
