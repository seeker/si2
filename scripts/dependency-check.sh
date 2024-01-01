#!/bin/sh
mvn versions:dependency-updates-aggregate-report
mvn versions:property-updates-aggregate-report
mvn versions:plugin-updates-aggregate-report
