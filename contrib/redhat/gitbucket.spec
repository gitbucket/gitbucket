Name:		gitbucket
Summary:	GitHub clone written with Scala.
Version:	1.7
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


%clean
[ "%{buildroot}" != / ] && %{__rm} -rf "%{buildroot}"


%files
%defattr(-,root,root,-)
%{_datarootdir}/%{name}/lib/%{name}.war
%{_sysconfdir}/init.d/%{name}
%config %{_sysconfdir}/sysconfig/%{name}
%{_localstatedir}/log/%{name}/run.log


%changelog
* Mon Oct 28 2013 Jiri Tyr <jiri_DOT_tyr at gmail.com>
- Version bump to v1.7.

* Thu Oct 17 2013 Jiri Tyr <jiri_DOT_tyr at gmail.com>
- First build.
