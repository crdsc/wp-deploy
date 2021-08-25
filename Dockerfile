#FROM mysql/mysql-server:5.7.35
# FROM mysql:5.6
FROM mariadb:latest as builder

# That file does the DB initialization but also runs mysql daemon, by removing the last line it will only init
RUN ["sed", "-i", "s/exec \"$@\"/echo \"not running $@\"/", "/usr/local/bin/docker-entrypoint.sh"]

# needed for intialization
ENV MYSQL_ROOT_PASSWORD=passw0rd

COPY ./sql-scripts/ /docker-entrypoint-initdb.d/

RUN ["/usr/local/bin/docker-entrypoint.sh", "mysqld", "--datadir", "/initialized-db", "--aria-log-dir-path", "/initialized-db"]

#FROM mysql:5.6
FROM mariadb:latest

COPY --from=builder /initialized-db /var/lib/mysql
