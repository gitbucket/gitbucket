Name:		gitbucket
Summary:	GitHub clone written with Scala.
Version:	2.6
Release:	1%{?dist}
License:	Apache
URL:		https://github.com/takezoe/gitbucket
Group:		System/Servers
Source0:	%{name}.war
Source1:	%{name}.init
Source2:	%{name}.conf
BuildRoot:	%{_tmppath}/%{name}-%{version}-%{release}-root
BuildArch:	noarch
Requires:	java >= 1.7


%description

GitBucket is the easily installable GitHub clone written with Scala.


%install
[ "%{buildroot}" != / ] && %{__rm} -rf "%{buildroot}"
%{__mkdir_p} %{buildroot}{%{_sysconfdir}/{init.d,sysconfig},%{_datarootdir}/%{name}/lib,%{_sharedstatedir}/%{name},%{_localstatedir}/log/%{name}}
%{__install} -m 0644 %{SOURCE0} %{buildroot}%{_datarootdir}/%{name}/lib
%{__install} -m 0755 %{SOURCE1} %{buildroot}%{_sysconfdir}/init.d/%{name}
%{__install} -m 0644 %{SOURCE2} %{buildroot}%{_sysconfdir}/sysconfig/%{name}
touch %{buildroot}%{_localstatedir}/log/%{name}/run.log

%pre
/usr/sbin/groupadd -r gitbucket &> /dev/null || :
/usr/sbin/useradd -g gitbucket -s /bin/false -r -c "GitBucket GitHub clone" -d %{_sharedstatedir}/%{name} gitbucket &> /dev/null || :

%post
/sbin/chkconfig --add gitbucket

%preun
if [ "$1" = 0 ]; then
  /sbin/service gitbucket stop > /dev/null 2>&1
  /sbin/chkconfig --del gitbucket
fi
exit 0

%postun
if [ "$1" -ge 1 ]; then
  /sbin/service gitbucket restart > /dev/null 2>&1
fi
exit 0

%clean
[ "%{buildroot}" != / ] && %{__rm} -rf "%{buildroot}"


%files
%defattr(-,root,root,-)
%{_datarootdir}/%{name}/lib/%{name}.war
%config %{_sysconfdir}/init.d/%{name}
%config(noreplace) %{_sysconfdir}/sysconfig/%{name}
%attr(0755,gitbucket,gitbucket) %{_sharedstatedir}/%{name}
%attr(0750,gitbucket,gitbucket) %{_localstatedir}/log/%{name}


%changelog
* Mon Nov 24 2014 Toru Takahashi <torutk at gmail.com>
- Version bump to v2.6

* Sun Nov 09 2014 Toru Takahashi <torutk at gmail.com>
- Version bump to v2.5

* Sun Oct 26 2014 Toru Takahashi <torutk at gmail.com>
- Version bump to v2.4.1

* Mon Jul 21 2014 Toru Takahashi <torutk at gmail.com>
- execute as gitbucket user

* Sun Jul 20 2014 Toru Takahashi <torutk at gmail.com>
- Version bump to v2.1.

* Mon Oct 28 2013 Jiri Tyr <jiri_DOT_tyr at gmail.com>
- Version bump to v1.7.

* Thu Oct 17 2013 Jiri Tyr <jiri_DOT_tyr at gmail.com>
- First build.
