#!/bin/bash

for vm in 'consul' 'mongodb' 'rabbitmq'
do
vagrant $1 $vm&
done
