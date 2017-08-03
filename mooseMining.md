# Mining Repos with MOOSE/MSE files
Doris is used in this fork to automate the mining of projects. The overall process looks like this:

![alternative text](http://www.plantuml.com/plantuml/proxy?src=https://raw.githubusercontent.com/profcfuhrmanets/doris/master/mooseMiningActivity.puml)

One advantage of using **doris** is that it supports selecting a range of commits from a projects history, via **-s \<commit sha-1\>** and **-e \<commit sha-1\>**. Many projects are very big and it's not feasible to analyse everything. 
