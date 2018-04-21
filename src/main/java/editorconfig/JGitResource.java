package editorconfig;

import org.ec4j.core.Resource;
import org.ec4j.core.ResourcePath;
import org.ec4j.core.model.Ec4jPath;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public class JGitResource implements Resource {
    private final Repository repo;
    private final String revStr;

    Ec4jPath path;

    private static String removeInitialSlash(Ec4jPath path) {
        return Ec4jPath.Ec4jPaths.root().relativize(path).toString();
    }

    public JGitResource(Git git, String revStr, String path){
        if (!path.startsWith("/")){
            path = "/" + path;
        }
        this.repo= git.getRepository();
        this.path = Ec4jPath.Ec4jPaths.of(path);
        this.revStr = revStr;
    }

    public JGitResource(Repository repo, String revStr, String path){
        if (!path.startsWith("/")){
            path = "/" + path;
        }
        this.repo = repo;
        this.path = Ec4jPath.Ec4jPaths.of(path);
        this.revStr = revStr;
    }


    public JGitResource(Repository repo, String revStr, Ec4jPath path){
        this.repo = repo;
        this.path = path;
        this.revStr = revStr;
    }

    private RevTree getRevTree() throws IOException {
        ObjectReader reader = repo.newObjectReader();
        try {
            RevWalk revWalk = new RevWalk(reader);
            ObjectId id = repo.resolve(revStr);
            RevCommit commit = revWalk.parseCommit(id);
            return commit.getTree();
        } finally {
            reader.close();
        }
    }

    @Override
    public boolean exists() {
        ObjectReader reader = repo.newObjectReader();
        try {
            TreeWalk treeWalk = TreeWalk.forPath(reader, removeInitialSlash(path), getRevTree());
            if (treeWalk != null){
                return true;
            }
            else {
                return false;
            }
        } catch (IOException e) {
            return false;
        } finally {
            reader.close();
        }
    }

    @Override
    public ResourcePath getParent() {
        Ec4jPath parent = path.getParentPath();
        return parent == null ? null : new JGitResourcePath(repo, revStr, path.getParentPath());
    }

    @Override
    public Ec4jPath getPath() {
        return path;
    }

    @Override
    public RandomReader openRandomReader() throws IOException {
        return Resources.StringRandomReader.ofReader(openReader());
    }

    @Override
    public Reader openReader() throws IOException {
        ObjectReader reader = repo.newObjectReader();
        try {
            TreeWalk treeWalk = TreeWalk.forPath(reader, removeInitialSlash(path), getRevTree());
            return new InputStreamReader(reader.open(treeWalk.getObjectId(0)).openStream(), StandardCharsets.UTF_8);
        } finally {
            reader.close();
        }
    }

    @Override
    public boolean equals(Object obj){
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        JGitResource other = (JGitResource) obj;
        if (!repo.equals(other.repo) || !revStr.equals(other.revStr) || !path.equals(other.path)){
            return false;
        }
        return true;
    }

    @Override
    public String toString(){
        return "JGitResouce(Repo:" + repo.getDirectory() + ", revStr:" + revStr + ", path:" + path.toString() + ")";
    }
}
