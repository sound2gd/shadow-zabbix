FROM java:8-alpine
MAINTAINER Your Name <you@example.com>

ADD target/uberjar/shadow-zabbix.jar /shadow-zabbix/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/shadow-zabbix/app.jar"]
