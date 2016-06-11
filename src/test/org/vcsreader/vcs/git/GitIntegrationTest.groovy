package org.vcsreader.vcs.git

import org.junit.Test
import org.vcsreader.VcsChange
import org.vcsreader.VcsProject
import org.vcsreader.vcs.Change
import org.vcsreader.vcs.Commit

import static org.vcsreader.VcsChange.Type.*
import static org.vcsreader.VcsChange.noRevision
import static org.vcsreader.lang.DateTimeUtil.date
import static org.vcsreader.lang.DateTimeUtil.dateTime
import static org.vcsreader.vcs.TestUtil.assertCommitsIn
import static org.vcsreader.vcs.git.GitIntegrationTestConfig.*
import static org.vcsreader.vcs.git.GitRepository.Scripts.*

class GitIntegrationTest {
	private static final gitSettings = GitSettings.defaults().withGitPath(GitIntegrationTestConfig.pathToGit)


	@Test void "clone project"() {
		def repository = new GitRepository(newReferenceRepoPath()).init()
		def project = new VcsProject(new GitVcsRoot(newProjectPath(), repository.path, gitSettings))

		def cloneResult = project.cloneToLocal()

		assert cloneResult.vcsErrors().empty
		assert cloneResult.isSuccessful()
	}

	@Test void "clone project failure"() {
		def vcsRoot = new GitVcsRoot(newProjectPath(), "/tmp/non-existent-repo-path", gitSettings)
		def project = new VcsProject(vcsRoot)

		def cloneResult = project.cloneToLocal()

		assert !cloneResult.isSuccessful()
		assert cloneResult.vcsErrors().size() == 1
	}

	@Test void "update project"() {
		def repository = someNonEmptyRepository()

		def project = newProject(repository)
		def updateResult = project.update()

		assert updateResult.vcsErrors().empty
		assert updateResult.isSuccessful()
	}

	@Test void "update project failure"() {
		def repository = new GitRepository(newReferenceRepoPath()).init()
		def project = newProject(repository)
		// delete project directory so that update fails
		assert new File((project.vcsRoots().first() as GitVcsRoot).localPath).deleteDir()

		def updateResult = project.update()

		assert !updateResult.isSuccessful()
		assert updateResult.vcsErrors() != []
	}

	@Test void "log no commits for empty project history interval"() {
		def repository = someNonEmptyRepository()

		def project = newProject(repository)
		def logResult = project.log(date("01/08/2014"), date("02/08/2014"))

		assert logResult.commits().empty
		assert logResult.vcsErrors().empty
		assert logResult.isSuccessful()
	}

	@Test void "log single commit from project history (start date is inclusive, end date is exclusive)"() {
		def repository = 'two commits with three added files'()
		def revisions = repository.revisions

		def project = newProject(repository)
		def logResult = project.log(date("10/08/2014"), date("11/08/2014"))

		assertCommitsIn(logResult, [
			new Commit(
				revisions[0], noRevision,
				dateTime("00:00:00 10/08/2014"),
				author,
				"initial commit",
				[new Change(ADDED, "file1.txt", revisions[0])]
			)
		])
	}

	@Test void "log several commits from project history"() {
		def repository = 'two commits with three added files'()
		def revisions = repository.revisions

		def project = newProject(repository)
		def logResult = project.log(date("10/08/2014"), date("12/08/2014"))

		assertCommitsIn(logResult, [
			new Commit(
				revisions[0], noRevision,
				dateTime("00:00:00 10/08/2014"),
				author,
				"initial commit",
				[new Change(ADDED, "file1.txt", revisions[0])]
			),
			new Commit(
				revisions[1], revisions[0],
				dateTime("00:00:00 11/08/2014"),
				author,
				"added file2, file3",
				[
					new Change(ADDED, "file2.txt", "", revisions[1], noRevision),
					new Change(ADDED, "file3.txt", "", revisions[1], noRevision)
				]
			)
		])
	}

	@Test void "log commit with modified files"() {
		def repository = 'two added and modified files'()
		def revisions = repository.revisions

		VcsProject project = newProject(repository)
		def logResult = project.log(date("12/08/2014"), date("13/08/2014"))

		assertCommitsIn(logResult, [
			new Commit(
				revisions[1], revisions[0],
				dateTime("14:00:00 12/08/2014"),
				author,
				"modified file1, file2",
				[
					new Change(MODIFIED, "file1.txt", "file1.txt", revisions[1], revisions[0]),
					new Change(MODIFIED, "file2.txt", "file2.txt", revisions[1], revisions[0])
				]
			)
		])
	}

	@Test void "log moved file commit"() {
		def repository = 'moved file'()
		def revisions = repository.revisions

		def project = newProject(repository)
		def logResult = project.log(date("13/08/2014"), date("14/08/2014"))

		assertCommitsIn(logResult, [
			new Commit(
				revisions[1], revisions[0],
				dateTime("14:00:00 13/08/2014"),
				author,
				"moved file",
				[new Change(MOVED, "folder/file.txt", "file.txt", revisions[1], revisions[0])]
			)
		])
	}

	@Test void "log moved and renamed file commit"() {
		def repository = 'moved and renamed file'()
		def revisions = repository.revisions

		def project = newProject(repository)
		def logResult = project.log(date("14/08/2014"), date("15/08/2014"))

		assertCommitsIn(logResult, [
			new Commit(
				revisions[1], revisions[0],
				dateTime("14:00:00 14/08/2014"),
				author,
				"moved and renamed file",
				[new Change(MOVED, "folder/renamed_file.txt", "file.txt", revisions[1], revisions[0])]
			)
		])
	}

	@Test void "log deleted file"() {
		def repository = 'repo with deleted file'()
		def revisions = repository.revisions

		def project = newProject(repository)
		def logResult = project.log(date("15/08/2014"), date("16/08/2014"))

		assertCommitsIn(logResult, [
			new Commit(
				revisions[1], revisions[0],
				dateTime("14:00:00 15/08/2014"),
				author,
				"deleted file",
				[new Change(DELETED, "", "file.txt", revisions[1], revisions[0])]
			)
		])
	}

	@Test void "log file with spaces and quotes in its name"() {
		def repository = 'repo with file with spaces and quotes'()
		def revisions = repository.revisions

		def project = newProject(repository)
		def logResult = project.log(date("16/08/2014"), date("17/08/2014"))

		assertCommitsIn(logResult, [
			new Commit(
				revisions[0], noRevision,
				dateTime("14:00:00 16/08/2014"),
				author,
				"added file with spaces and quotes",
				[new Change(ADDED, '"file with spaces.txt"', "", revisions[0], noRevision)]
			)
		])
	}

	@Test void "log commit with non-ascii message and file content"() {
		def repository = 'repo with non-ascii file name and commit message'()
		def revisions = repository.revisions

		def project = newProject(repository)
		def logResult = project.log(date("17/08/2014"), date("18/08/2014"))

		assertCommitsIn(logResult, [
			new Commit(
				revisions[0], noRevision,
				dateTime("15:00:00 17/08/2014"),
				author,
				"non-ascii комментарий",
				[new Change(ADDED, "non-ascii.txt", "", revisions[0], noRevision)]
			)
		])
		def nonAsciiContent = logResult.commits().first().changes.first().fileContent().value
		assert nonAsciiContent == "non-ascii содержимое"
	}

	@Test void "log commit with empty message"() {
		def repository = new GitRepository(newReferenceRepoPath()).init().with {
			create("file.txt")
			commit("", "Aug 18 16:00:00 2014 +0000")
			it
		}
		def revisions = repository.revisions

		def project = newProject(repository)
		def logResult = project.log(date("18/08/2014"), date("19/08/2014"))

		assertCommitsIn(logResult, [
			new Commit(
				revisions[0], noRevision,
				dateTime("16:00:00 18/08/2014"),
				author,
				"",
				[new Change(ADDED, "file.txt", "", revisions[0], noRevision)]
			)
		])
	}

	@Test void "log commit with no changes"() {
		def repository = new GitRepository(newReferenceRepoPath()).init().with {
			commit("commit with no changes", "Aug 19 17:00:00 2014 +0000")
			it
		}
		def revisions = repository.revisions

		def project = newProject(repository)
		def logResult = project.log(date("19/08/2014"), date("20/08/2014"))

		assertCommitsIn(logResult, [
			new Commit(
				revisions[0], noRevision,
				dateTime("17:00:00 19/08/2014"),
				author,
				"commit with no changes",
				[]
			)
		])
	}

	@Test void "log branch rebase"() {
		def repository = new GitRepository(newReferenceRepoPath()).init().with {
			create("file-master.txt", "file-master content")
			commit("added file-master.txt", "Aug 20 18:10:00 2014 +0000")

			createAndCheckoutBranch("a-branch")
			create("file-branch.txt", "file branch content")
			commit("added file-branch.txt", "Aug 20 18:00:00 2014 +0000")

			// note that on rebase committer date changes but author date doesn't
			rebase("master", "added file-branch.txt", "Aug 20 18:20:00 2014 +0000")
			it
		}
		def revisions = repository.revisions

		def project = newProject(repository)
		def logResult = project.log(date("20/08/2014"), date("21/08/2014"))

		assertCommitsIn(logResult, [
			new Commit(
				revisions[1], revisions[0],
				dateTime("18:00:00 20/08/2014"),
				author,
				"added file-branch.txt",
				[new Change(ADDED, "file-branch.txt", "", revisions[1], noRevision)]
			),
			new Commit(
				revisions[0], noRevision,
				dateTime("18:10:00 20/08/2014"),
				author,
				"added file-master.txt",
				[new Change(ADDED, "file-master.txt", "", revisions[0], noRevision)]
			)
		])
	}

	@Test void "log branch merge"() {
		def repository = new GitRepository(newReferenceRepoPath()).init().with {
			// need this commit to force git create master branch
			create("dummy-file.txt")
			commit("added dummy file", "Aug 20 18:10:00 2014 +0000")

			createAndCheckoutBranch("a-branch")
			create("file-branch.txt", "file branch content")
			commit("added file-branch.txt", "Aug 21 19:00:00 2014 +0000")

			checkoutBranch("master")
			create("file-master.txt", "file-master content")
			commit("added file-master.txt", "Aug 21 19:10:00 2014 +0000")
			mergeBranch("a-branch", "merged branch into master", "Aug 21 19:20:00 2014 +0000")
			it
		}
		def revisions = repository.revisions

		def project = newProject(repository)
		def logResult = project.log(date("21/08/2014"), date("22/08/2014"))

		assertCommitsIn(logResult, [
			new Commit(
				revisions[1], revisions[0],
				dateTime("19:00:00 21/08/2014"),
				author,
				"added file-branch.txt",
				[new Change(ADDED, "file-branch.txt", "", revisions[1], noRevision)]
			),
			new Commit(
				revisions[2], revisions[0], // note the revision "jump"
				dateTime("19:10:00 21/08/2014"),
				author,
				"added file-master.txt",
				[new Change(ADDED, "file-master.txt", "", revisions[2], noRevision)]
			)
		])
	}

	@Test void "log content of modified file"() {
		def repository = 'two added and modified files'()

		def project = newProject(repository)
		def logResult = project.log(date("12/08/2014"), date("13/08/2014"))

		def change = logResult.commits().first().changes.first()
		assert change.type == MODIFIED
		assert change.fileContent().value == "file1 new content"
		assert change.fileContentBefore().value == "file1 content"

		assert logResult.isSuccessful()
	}

	@Test void "log content of new file"() {
		def repository = 'two added and modified files'()

		def project = newProject(repository)
		def logResult = project.log(date("11/08/2014"), date("12/08/2014"))

		def change = logResult.commits().first().changes.first()
		assert change.type == ADDED
		assert change.fileContent().value == "file1 content"
		assert change.fileContentBefore() == VcsChange.FileContent.none

		assert logResult.isSuccessful()
	}

	@Test void "log content of deleted file"() {
		def repository = 'repo with deleted file'()

		def project = newProject(repository)
		def logResult = project.log(date("15/08/2014"), date("16/08/2014"))

		def change = logResult.commits().first().changes.first()
		assert change.type == DELETED
		assert change.fileContent() == VcsChange.FileContent.none
		assert change.fileContentBefore().value == "file content"

		assert logResult.isSuccessful()
	}


	private static VcsProject newProject(GitRepository repository) {
		def project = new VcsProject(new GitVcsRoot(newProjectPath(), repository.path, gitSettings))
		project.cloneToLocal()
		project
	}

}
