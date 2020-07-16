job("Pull data from Github"){
  description("Pull the data from github repo automatically when some developers push code to github")
  scm{
    github("surinder2000/Deploy-web-app-on-kubernetes-using-jenkins-DSL-scripting","master")
  }
  triggers {
    scm("* * * * *")
  }
  steps{
    shell('''if ls /root | grep webdata
then
sudo cp -rf * /root/webdata
else
mkdir /root/webdata
sudo cp -rf * /root/webdata
fi  
''')
  }
}

job("Launch deployment"){
  description("By looking at the code it will launch the deployment of respective webserver and the deployment will launch webserver, create PVC and expose the deployment")

  triggers {
    upstream("Pull data from Github", "SUCCESS")
  }
  steps{
    shell('''data=$(sudo ls /root/webdata)
if sudo ls /root/webdata/ | grep html
then
if sudo kubectl get deploy/html-webserver
then
echo "Already running"
POD=$(sudo kubectl get pod -l server=apache-httpd -o jsonpath="{.items[0].metadata.name}")
for file in $data
do
sudo kubectl cp /root/webdata/$file $POD:/usr/local/apache2/htdocs/
done
else
sudo kubectl create -f /root/webdata/htmlweb.yml
POD=$(sudo kubectl get pod -l server=apache-httpd -o jsonpath="{.items[0].metadata.name}")
sleep 30
for file in $data
do
sudo kubectl cp /root/webdata/$file $POD:/usr/local/apache2/htdocs/
done
fi
elif sudo ls /root/webdata/ | grep php
then
if sudo kubectl get deploy/php-webserver
then
echo "Already running"
POD=$(sudo kubectl get pod -l server=apache-httpd-php -o jsonpath="{.items[0].metadata.name}")
for file in $data
do
sudo kubectl cp /root/webdata/$file $POD:/var/www/html/
done
else
sudo kubectl create -f /root/webdata/phpweb.yml
POD=$(sudo kubectl get pod -l server=apache-httpd -o jsonpath="{.items[0].metadata.name}")
sleep 30
for file in $data
do
sudo kubectl cp /root/webdata/$file $POD:/var/www/html/
done
fi
else
echo "No server found"
exit 1
fi
''')
  }
}


job("Check status of site"){
  description("Check whether the web app is working or not. If it is not working sent email to developer")
  triggers {
    upstream("Launch deployment", "SUCCESS")
  }
  steps{
    shell('''if sudo ls /root/webdata | grep html
then
status=$(sudo curl -o /dev/null -s -w "%{http_code}" 192.168.99.111:31001)
elif sudo ls /root/webdata | grep php
then
status=$(sudo curl -o /dev/null -s -w "%{http_code}" 192.168.99.111:31002)
fi
if [[ status -ne 200 ]]
then 
if sudo ls /root/webdata/ | grep html
then
sudo kubectl delete -f /root/webdata/htmlweb.yml
exit 1
elif sudo ls /root/webdata/ | grep php
then
sudo kubectl delete -f /root/webdata/phpweb.yml
exit 1
fi
else
exit 0
fi
''')
  }
  publishers {
    extendedEmail {
      recipientList("surinderkumarmanhas901@gmail.com")
      defaultSubject("Status of site")
      defaultContent("Site is not working fine. Please check the code")
      contentType("text/plain")
      triggers {
        failure {
          sendTo {
            recipientList()
          }
        }
      }
    }
  }
}

job("Email notification"){
  description("Send email notification to developer if site is working fine")

  triggers {
    upstream("Check status of site", "SUCCESS")
  }
  publishers {
    extendedEmail {
      recipientList("surinderkumarmanhas901@gmail.com")
      defaultSubject("Status of site")
      defaultContent("Site is working fine")
      contentType("text/plain")
      triggers {
        always {
          sendTo {
            recipientList()
          }
        }
      }
    }
  }
}

buildPipelineView("Pipeline view") {
  filterBuildQueue(true)
  filterExecutors(false)
  title("My pipeline")
  displayedBuilds(1)
  selectedJob("Pull data from Github")
  alwaysAllowManualTrigger(true)
  showPipelineParameters(true)
  refreshFrequency(10)
}

