package editorconfig;

import org.ec4j.core.Resource;
import org.ec4j.core.ResourcePath;
import org.ec4j.core.model.Ec4jPath;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;

public class JGitResourcePath implements ResourcePath {
    private final Repository repo;
    private final String revStr;
    private final Ec4jPath path;

    public JGitResourcePath(Repository repo, String revStr, Ec4jPath path){
        this.repo= repo;
        this.revStr = revStr;
        this.path = path;
    }

    public static JGitResourcePath RootDirectory(Git git, String revStr){
        return new JGitResourcePath(git.getRepository(), revStr, Ec4jPath.Ec4jPaths.of("/"));
    }

    @Override
    public ResourcePath getParent() {
        Ec4jPath parent = path.getParentPath();
        return parent == null ? null : new JGitResourcePath(repo, revStr, parent);
    }

    @Override
    public Ec4jPath getPath() {
        return path;
    }

    @Override
    public boolean hasParent() {
        return path.getParentPath() != null;
    }

    @Override
    public Resource relativize(Resource resource) {
        if (resource instanceof JGitResource) {
            JGitResource jgitResource = (JGitResource) resource;
            return new JGitResource(repo, revStr, path.relativize(jgitResource.path).toString());
        } else {
            throw new IllegalArgumentException(
                this.getClass().getName() + ".relativize(Resource resource) can handle only instances of "
                    + JGitResource.class.getName());
        }
    }

    @Override
    public Resource resolve(String name) {
        if(path == null){
            return new JGitResource(repo, revStr, name);
        }
        else {
            return new JGitResource(repo, revStr, path.resolve(name));
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
        JGitResourcePath other = (JGitResourcePath) obj;
        if (!repo.equals(other.repo) || !revStr.equals(other.revStr) || !path.equals(other.path)){
            return false;
        }
        return true;
    }

    @Override
    public String toString(){
        return "JGitResoucePath(Repo:" + repo.getDirectory() + ", revStr:" + revStr + ", path:" + path.toString() + ")";
    }
}
