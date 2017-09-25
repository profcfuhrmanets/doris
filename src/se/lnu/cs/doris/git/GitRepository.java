package se.lnu.cs.doris.git;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

import se.lnu.cs.doris.global.ExceptionHandler;
import se.lnu.cs.doris.global.GlobalMessages;
import se.lnu.cs.doris.global.OutOfSpaceException;
import se.lnu.cs.doris.global.Utilities;

/**
 * Class to perform repository mining of git repositories.
 * 
 * @author Emil Carlsson
 * 
 * This file is a part of Doris
 *
 * Doris is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, either version 3 of the License, or 
 * (at your option) any later version.
 * Doris is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with Doris.  
 * If not, see <http://www.gnu.org/licenses/>.
 *
 */
public class GitRepository {
	//Class specific constants
	//Because of IO-time compensation there is a minimum of 4 threads. Else double the amount of available cores.
	private final int MAX_NUMBER_OF_THREADS = (Runtime.getRuntime().availableProcessors() < 2) ? 4 : Runtime.getRuntime().availableProcessors() * 2;
	
	//Class usage
	private Repository m_headRepository;
	private String m_repoName;
	private int m_runningThreads = 0;

	//Inputs
	private String m_uri;
	private String m_target;
	private String m_startPoint;
	private String m_endPoint;
	private String m_branch;
	private Boolean m_noLog;
	private Set<String> m_shaSet;

	private int m_limit;

	//Strings
	private String m_master = "master";

	/**
	 * Constructor taking the uri flag value.
	 * @param uri URI to the git repository's .git file.
	 */
	public GitRepository(String uri) {		
		this(uri, null, null, null, null, 0, false, null);
	}

	/**
	 * Constructor taking the URI and target flags.
	 * @param uri URI to the git repository's .git file.
	 * @param target Path to target directory.
	 */
	public GitRepository(String uri, String target) {
		this(uri, target, null, null, null, 0, false, null);
	}

	/**
	 * Constructor taking the URI, target and start point flags.
	 * @param uri URI to the git repository's .git file.
	 * @param target Path to target directory.
	 * @param branch Name of the branch to pull.
	 */
	public GitRepository(String uri, String target, String branch) {
		this(uri, target, branch, null, null, 0, false, null);
	}

	/**
	 * Constructor taking the URI, target and start point flags.
	 * @param uri URI to the git repository's .git file.
	 * @param target Path to target directory.
	 * @param startPoint SHA-1 checksum of the commit that is the starting point.
	 */
	public GitRepository(String uri, String target, String branch, String startPoint) {
		this(uri, target, branch, startPoint, null, 0, false, null);
	}

	/**
	 * Constructor taking the URI, target, start point flags and end point.
	 * @param uri URI to the git repository's .git file.
	 * @param target Path to target directory.
	 * @param startPoint SHA-1 checksum of the commit that is the starting point.
	 * @param endPoint SHA-1 checksum of the commit that is the end point.
	 */
	public GitRepository(String uri, String target, String branch, String startPoint,
			String endPoint) {
		this(uri, target, branch, startPoint, endPoint, 0, false, null);
	}

	/**
	 * Constructor taking the URI, target, start point flags and end point.
	 * @param uri URI to the git repository's .git file.
	 * @param target Path to target directory.
	 * @param startPoint SHA-1 checksum of the commit that is the starting point.
	 * @param endPoint SHA-1 checksum of the commit that is the end point.
	 * @param limit Number of commits to be mined.
	 */
	public GitRepository(String uri, String target, String branch, String startPoint,
			String endPoint, int limit) {
		this(uri, target, branch, startPoint, endPoint, limit, false, null);
	}
	
	public GitRepository(GitParameters params) {
		//TODO: Create functionality that a GitParameters object can be passed.
	}

	/**
	 * Constructor taking the URI, target, start point flags and end point.
	 * @param uri URI to the git repository's .git file.
	 * @param target Path to target directory.
	 * @param startPoint SHA-1 checksum of the commit that is the starting point.
	 * @param endPoint SHA-1 checksum of the commit that is the end point.
	 * @param limit Number of commits to be mined.
	 * @param noLog Boolean to set if a meta-data log should be created or not.
	 */
	public GitRepository(String uri, String target, String branch, String startPoint,
			String endPoint, int limit, Boolean noLog, Set<String> shaSet) {
		//TODO: Refactor this constructor.
		//Get repository name.
		this.m_repoName = this.getRepoNameFromUri(uri);

		//Set target dir and append repository name.
		if (target == null) {
			target = System.getProperty("user.dir");
		}		
		
		this.m_branch = (branch == null) ? this.m_master : branch;
		
		this.m_target = this.appendProjectName(target, uri);
		
		File file = new File(this.m_target);
		
		if (file.exists()) {
			if (!GlobalMessages.overWriteFolder(this.m_target)) {
				this.m_target += "_" + Utilities.getNumeralFileAppendix(this.m_target);
			} else {
				Utilities.deleteDirectory(file);
			}
		}
		
		//Set all in parameters.
		this.m_uri = uri;
		this.m_noLog = noLog;
		this.m_startPoint = startPoint;
		this.m_endPoint = endPoint;
		this.m_limit = limit;

		//Set head repository to null.
		this.m_headRepository = null;

		this.m_shaSet = shaSet;
	}

	/**
	 * Method to fetch a bare .git repository for local storage.
	 * @throws Exception 
	 */
	public void pullBare() throws Exception {
		String barePath = this.m_target + "/" + this.m_repoName + "_" + this.m_branch + ".git";
		File file = new File(barePath);
		
		if (file.exists()) {
			Utilities.deleteDirectory(file);
		}
		try {
			Git git = Git.cloneRepository()
					.setURI(this.m_uri)
					.setDirectory(new File(this.m_target, this.m_repoName + "_" + this.m_branch + ".git"))
					.setCloneAllBranches(true)
					.setBare(true)
					.setBranch(m_branch)
					.call();
			
			this.m_headRepository = git.getRepository();
			
		} catch (Exception e) {
			this.errorHandlingMining(e, null);
		}

	}

	/**
	 * Method to do start mining a repository.
	 * @throws Exception 
	 */
	public void mine() throws Exception {
		//Fetch the bare .git file to continue working locally only.
		this.pullBare();
		RevWalk rw = this.getRevWalk();
		
		try {
			//Order all commits from first to last.
			AnyObjectId headId = this.m_headRepository.resolve(Constants.HEAD);
			int i = 0;
			rw.sort(RevSort.REVERSE);

			RevCommit root = rw.parseCommit(headId);
			rw.markStart(root);

			Iterator<RevCommit> revs = rw.iterator();
			
			RevCommit current = rw.parseCommit(revs.next());
			// if start but no end, then only do one iteration
			Boolean startFound = this.m_startPoint == null,
					singleIteration = (this.m_startPoint != null) && (this.m_endPoint == null),
					isRange = this.m_shaSet == null,
					checkLimit = this.m_limit != 0;
			int limit = 0;
			System.out.println("isRange = " + isRange);
			if (!isRange) System.out.println("shaSet = " + m_shaSet);

			while (true) {
				if (isRange) {
					// stop if limit reached
					if (checkLimit && this.m_limit <= limit) {
						waitThreads();
						GlobalMessages.miningDone(this.m_repoName);
						break;
					}
					// stop if found end
					if (this.m_endPoint != null) {
						if (current.getName().toLowerCase().equals(this.m_endPoint.toLowerCase())) {
							waitThreads();
							GlobalMessages.miningDone(this.m_repoName);
							break;
						}
					}
					// detect the start of a range
					if (!startFound) {
						if (current.getName().toLowerCase().equals(this.m_startPoint.toLowerCase())) {
							startFound = true;
						}
					}
					// if inside the range
					if (startFound) {
						startCloner(i, current);
						limit++;
					}
				}
				else {
					if (m_shaSet.contains(current.getName().toLowerCase())) {
						System.out.println("Found SHA " + current.getName().toLowerCase());
						startCloner(i, current);
						limit++;
					}
				}
				if (revs.hasNext() && (!singleIteration || !startFound)) {
					current = rw.parseCommit(revs.next());
					i++;
				} else {
					waitThreads();
					GlobalMessages.miningDone(this.m_repoName);
					break;
				}
			}
		} catch (Exception e) {
			this.errorHandlingMining(e, null);
		}
		
		if (this.m_headRepository != null) {
			this.m_headRepository.close();
		}
	}

	private void startCloner(int i, RevCommit current) throws Exception {
		//Should work, but not the most beautiful solution.
		Object monitor = this;
		while (this.m_runningThreads > MAX_NUMBER_OF_THREADS) {
			synchronized (monitor) {
				try {
					monitor.wait();
				} catch (Exception e) {
					e.printStackTrace();
					System.exit(-1);
				}
			}
		}
		String name = Integer.toString(i);
		File file = new File(this.m_target + "/" + name);

		//If the commit already have been mined we remove it in case it's
		//an older version or another program.
		if (file.exists()) {
            Utilities.deleteDirectory(file);
        }

		this.m_runningThreads++;

		new Cloner(current, name, i, monitor);

		if (!this.m_noLog) {
            GitLogger.addNode(this.m_target, this.m_repoName, name, current);
        }
	}

	private void waitThreads() throws InterruptedException {
		//Ugly but working to clean up after multiple threads.
		while (this.m_runningThreads > 0) {
            //System.out.println("m_runningThreads = " + this.m_runningThreads);
            Thread.sleep(200);
        }
	}

	/**
	 * Minimal error handling
	 * @param e Exception thrown.
	 * @param current RevCommit in case one was loaded.
	 * @throws Exception 
	 */
	private void errorHandlingMining(Exception e, RevCommit current) throws Exception {
		if (e.getMessage().contains("not enough space")) {
			throw new OutOfSpaceException(e.getMessage(), (current != null ? current.getName() : "Initial commit"));
		} else {
			throw e;
		}
	}

	/**
	 * Private function to fetch a revision walk from the head repository.
	 * @return RevWalk
	 * @throws Exception 
	 */
	private RevWalk getRevWalk() throws Exception {
		if (this.m_headRepository == null) {
			this.pullBare();
		}
		
		return new RevWalk(this.m_headRepository);
	}

	/**
	 * Add project name to target string. Used to create a new directory at the
	 * target directory with the project name.
	 * @param target
	 * @return String
	 */
	public String appendProjectName(String target, String uri) {
		target = target.replace('\\', '/');
		if (target.charAt(target.length() - 1) == '/') {
			target = target.substring(0, target.length() - 1);
		}
		if (target.toLowerCase().endsWith(getRepoNameFromUri(uri).toLowerCase())) {
			return target;
		} else {
			return target + "/" + getRepoNameFromUri(uri) + "_" + this.m_branch;
		}
	}

	/**
	 * Method to extract project name from the URI.
	 * @param uri
	 * @return String
	 */
	public String getRepoNameFromUri(String uri) {
		return uri.substring(uri.lastIndexOf("/") + 1, uri.indexOf(".git"));
	}
	
	/**
	 * Get the target value.
	 * @return String
	 */
	public String getTarget() {
		return this.m_target;
	}

	/**
	 * Get the project name.
	 * @return String
	 */
	public String getProjectName() {
		return this.m_repoName;
	}
	
	class Cloner implements Runnable {

		private String m_name;
		private int m_i;
		private RevCommit m_current;
		private Thread m_thread;
		private Object m_monitor;

		public Cloner(RevCommit current, String name, int i, Object monitor) throws Exception {
			this.m_current = current;
			this.m_name = name;
			this.m_i = i;
			this.m_monitor = monitor;
			this.m_thread = new Thread(this);
			this.m_thread.start();
		}
		
		private void cloneCommit() throws Exception {
			System.out.println("cloneCommit(): " + m_target + ", " + m_name);
			ObjectReader objectReader = m_headRepository.newObjectReader();
			try {
				File mineDir = new File(m_target, this.m_name);
				
				if (!mineDir.exists()) {
					mineDir.mkdir();
					mineDir.setWritable(true);
					mineDir.setExecutable(true);
				}
				
				TreeWalk treeWalk = new TreeWalk(objectReader);
				treeWalk.addTree(m_current.getTree());
				
				while (treeWalk.next()) {
					if (treeWalk.getFileMode(0) == FileMode.GITLINK) {
						System.out.println("Ignoring " + treeWalk.getPathString() + " because it's a GITLINK (submodule?)");
						break;  // skip symlinks
					}
					String path = treeWalk.getPathString();
					File file = new File(mineDir, path);
					if (treeWalk.isSubtree()) {
						file.mkdir();
						treeWalk.enterSubtree();
					} else {
						FileOutputStream outputStream = null;
						ObjectId objectId = treeWalk.getObjectId(0);
						try {
							outputStream = new FileOutputStream(file);
							ObjectLoader objectLoader = objectReader.open(objectId);
							objectLoader.copyTo(outputStream);
						}
						catch (org.eclipse.jgit.errors.MissingObjectException e) {
							System.out.println("cloneCommit: Caught exception in treeWalk");
							System.out.println("cloneCommit: objectId is '" + objectId.toString() + "'");
							System.out.println("treeWalk.getFileMode(0): '" + treeWalk.getFileMode(0) + "'");
							System.out.println("This may be a link to another repo"); // TODO recognize links to other repos e.g.,
						} catch (Exception e) {
							System.out.println("cloneCommit: Caught exception in treeWalk");
							e.printStackTrace();
							throw e;
						} finally {
							if (outputStream != null) outputStream.close();
						}

						if (FileMode.EXECUTABLE_FILE.equals(treeWalk.getRawMode(0))) {
							file.setExecutable(true);
						}
					}
				}
				
				GlobalMessages.commitPulled(this.m_i, this.m_current.getName());
				m_runningThreads--;

			} catch (Exception e) {
				errorHandlingMining(e, this.m_current);
			} finally {
				// Release resources
				objectReader.release();
			}
		}
		
		@Override
		public void run() {	
			this.guardedCloner();
		}
		
		public void guardedCloner() {

			synchronized (this.m_monitor) {
				try {
					this.cloneCommit();
				} catch (Exception e) {
					if (e.getMessage().contains("not enough space")) {
						ExceptionHandler.HandleException(new OutOfSpaceException(e.getMessage(), (this.m_current != null ? this.m_current.getName() : "Initial commit")));
					} else {
						ExceptionHandler.HandleException(e);
					}
				}
				// in case waiting for threads to finish
				this.m_monitor.notifyAll();
			}
		}
	}
}
