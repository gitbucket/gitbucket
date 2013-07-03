gitbucket
=========

Github clone by Scala, Easy to setup.

## Run GitBucket server

```
$ sbt
> container:start
```

The gitbucket stores a database and repositories in `~/gitbucket`

## User home

```
http://localhost:8080/[user]
```

## Clone your repository to your local

```
$ git clone http://localhost:8080/[user]/[repos].git
```

Default username and password is gitbucket/password.
