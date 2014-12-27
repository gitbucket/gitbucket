# Contrib Notes #

RPM spec file and init script for Red Hat Enterprise Linux 6.x. 

To create RPM:
1. Edit `../../gitbucket.conf` to suit.
2. Edit `gitbucket.init` to suit.
3. Edit `gitbucket.spec` to suit.
4. Place `gitbucket.spec` to rpm/SPECS/.
5. Place `gitbucket.init` and `gitbucket.war` to rpm/SOURCES/.
6. Execute `rpmbuild -ba rpm/SPECS/gitbucket.spec`

This rpm runs gitbucket not as root user but as gitbucket user.
This rpm creates user and group named `gitbucket` at installation.
This rpm make chkconfig of gitbucket to be on.
