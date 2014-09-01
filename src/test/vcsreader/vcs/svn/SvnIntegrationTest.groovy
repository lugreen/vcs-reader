package vcsreader.vcs.svn

import org.junit.Test
import vcsreader.Change
import vcsreader.Commit
import vcsreader.VcsProject

import static org.hamcrest.CoreMatchers.equalTo
import static org.junit.Assert.assertThat
import static vcsreader.Change.Type.*
import static vcsreader.Change.noRevision
import static vcsreader.lang.DateTimeUtil.date
import static vcsreader.lang.DateTimeUtil.dateTime
import static vcsreader.vcs.svn.SvnIntegrationTestConfig.*

class SvnIntegrationTest {
    private final vcsRoots = [new SvnVcsRoot(repositoryUrl, SvnSettings.defaults())]
    private final project = new VcsProject(vcsRoots)

    @Test void "init project"() {
        def initResult = project.init().awaitResult()
        assert initResult.errors() == []
        assert initResult.isSuccessful()
    }

    @Test void "init project never fails"() {
        def vcsRoots = [new SvnVcsRoot(nonExistentUrl, SvnSettings.defaults())]
        def project = new VcsProject(vcsRoots)

        def initResult = project.init().awaitResult()
        assert initResult.isSuccessful()
        assert initResult.errors().isEmpty()
    }

    @Test void "update project"() {
        project.init().awaitResult()
        def updateResult = project.update().awaitResult()
        assert updateResult.errors() == []
        assert updateResult.isSuccessful()
    }

    @Test void "log single commit from project history"() {
        project.init().awaitResult()
        def logResult = project.log(date("10/08/2014"), date("11/08/2014")).awaitResult()

        assertEqualCommits(logResult.commits, [
                new Commit(
                        firstRevision, noRevision,
                        dateTime("14:00:00 10/08/2014"),
                        author,
                        "initial commit",
                        [new Change(NEW, "file1.txt", firstRevision)]
                )
        ])
        assert logResult.errors() == []
        assert logResult.isSuccessful()
    }

    @Test void "log several commits from project history"() {
        project.init().awaitResult()
        def logResult = project.log(date("10/08/2014"), date("12/08/2014")).awaitResult()

        assertEqualCommits(logResult.commits, [
                new Commit(
                        firstRevision, noRevision,
                        dateTime("14:00:00 10/08/2014"),
                        author,
                        "initial commit",
                        [new Change(NEW, "file1.txt", firstRevision)]
                ),
                new Commit(
                        secondRevision, firstRevision,
                        dateTime("14:00:00 11/08/2014"),
                        author,
                        "added file2, file3",
                        [
                            new Change(NEW, "file3.txt", "", secondRevision, noRevision),
                            new Change(NEW, "file2.txt", "", secondRevision, noRevision)
                        ]
                )
        ])
        assert logResult.errors() == []
        assert logResult.isSuccessful()
    }

    @Test void "log modification commit"() {
        project.init().awaitResult()
        def logResult = project.log(date("12/08/2014"), date("13/08/2014")).awaitResult()

        assertEqualCommits(logResult.commits, [
                new Commit(
                        thirdRevision, secondRevision,
                        dateTime("14:00:00 12/08/2014"),
                        author,
                        "modified file2, file3",
                        [
                            new Change(MODIFICATION, "file3.txt", "file3.txt", thirdRevision, secondRevision),
                            new Change(MODIFICATION, "file2.txt", "file2.txt", thirdRevision, secondRevision)
                        ]
                )
        ])
        assert logResult.errors() == []
        assert logResult.isSuccessful()
    }

    @Test void "log moved file commit"() {
        project.init().awaitResult()
        def logResult = project.log(date("13/08/2014"), date("14/08/2014")).awaitResult()

        assertEqualCommits(logResult.commits, [
                new Commit(
                        revision(4), revision(3),
                        dateTime("14:00:00 13/08/2014"),
                        author,
                        "moved file1",
                        [new Change(MOVED, "folder1/file1.txt", "file1.txt", revision(4), revision(1))]
                )
        ])
        assert logResult.errors() == []
        assert logResult.isSuccessful()
    }

    @Test void "log moved and renamed file commit"() {
        project.init().awaitResult()
        def logResult = project.log(date("14/08/2014"), date("15/08/2014")).awaitResult()

        assertEqualCommits(logResult.commits, [
                new Commit(
                        revision(5), revision(4),
                        dateTime("14:00:00 14/08/2014"),
                        author,
                        "moved and renamed file1",
                        [new Change(MOVED, "folder2/renamed_file1.txt", "folder1/file1.txt", revision(5), revision(4))]
                )
        ])
        assert logResult.errors() == []
        assert logResult.isSuccessful()
    }

    @Test void "log deleted file"() {
        project.init().awaitResult()
        def logResult = project.log(date("15/08/2014"), date("16/08/2014")).awaitResult()

        assertEqualCommits(logResult.commits, [
                new Commit(
                        revision(6), revision(5),
                        dateTime("14:00:00 15/08/2014"),
                        author,
                        "deleted file1",
                        [new Change(DELETED, "", "folder2/renamed_file1.txt", revision(6), revision(5))]
                )
        ])
        assert logResult.errors() == []
        assert logResult.isSuccessful()
    }

    @Test void "log content of modified file"() {
        project.init().awaitResult()
        def logResult = project.log(date("12/08/2014"), date("13/08/2014")).awaitResult()

        def change = logResult.commits.first().changes.first()
        assert change.type == MODIFICATION
        assert change.content() == "file2 new content"
        assert change.contentBefore() == "file2 content"
        assert logResult.errors() == []
        assert logResult.isSuccessful()
    }

//    @Test void "log content of new file"() {
//        project.init().awaitResult()
//        def logResult = project.log(date("11/08/2014"), date("12/08/2014")).awaitResult()
//
//        def change = logResult.commits.first().changes.first()
//        assert change.type == NEW
//        assert change.content() == "file2 content"
//        assert change.contentBefore() == null
//        assert logResult.errors() == []
//        assert logResult.isSuccessful()
//    }
//
//    @Test void "log content of deleted file"() {
//        project.init().awaitResult()
//        def logResult = project.log(date("15/08/2014"), date("16/08/2014")).awaitResult()
//
//        def change = logResult.commits.first().changes.first()
//        assert change.type == DELETED
//        assert change.content() == null
//        assert change.contentBefore() == "file1 content"
//        assert logResult.errors() == []
//        assert logResult.isSuccessful()
//    }

    static assertEqualCommits(List<Commit> actual, List<Commit> expected) {
        assertThat(actual.join("\n\n"), equalTo(expected.join("\n\n")))
    }
}
