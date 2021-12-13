# Openshift CI/CD for simple Java SpringBoot App with Maven

This Project Handle the basic CI/CD of Java application using Maven   

To run locally with Maven installed:   

```
mvn test // to run the unit tests
mvn clean test spring-boot:run // to build, test and run the application
mvn package //to build the jar file of this SpringBoot app

```
To deploy the application directly into Openshift using s2i you can use the folloing:
```
oc new-app --name=loyalty java~https://github.com/osa-ora/simple_java_maven
oc expose svc/loyalty
```

To use Jenkins on Openshift for CI/CD, Openshift come with Jenkins Maven Slave template to use in our CI/CD 

## 1) Build The Environment

Provision Jenkins and add the required privilages to dpeloy into different projects :  

```
oc new-project dev //this is project for application development
oc new-project cicd //this is the project for cicd

oc new-app jenkins-persistent  -p MEMORY_LIMIT=2Gi  -p VOLUME_CAPACITY=4Gi -n cicd
oc policy add-role-to-user edit system:serviceaccount:cicd:default -n dev
oc policy add-role-to-user edit system:serviceaccount:cicd:jenkins -n dev
```


## 2) Configure Jenkins 
No need as Jenkins already come with pre-configured maven slave image.
From inside Jenkins --> go to Manage Jenkins ==> Configure Jenkins then scroll to cloud section:
https://{JENKINS_URL}/configureClouds
Now click on Pod Templates, Check the template named "maven" already exist:

See the picture:
<img width="1412" alt="Screen Shot 2021-01-03 at 19 18 43" src="https://user-images.githubusercontent.com/18471537/103484585-96132f80-4df8-11eb-94c2-469a489077af.png">

## 3) (Optional) SonarQube on Openshift
Provision SonarQube for code scanning on Openshift using the attached template.
```
oc process -f https://raw.githubusercontent.com/osa-ora/simple_java_maven/main/cicd/sonarqube-persistent-template.yaml | oc create -f -

Or 
oc create -f https://raw.githubusercontent.com/osa-ora/simple_java_maven/main/cicd/sonarqube-persistent-template.yaml
Then provision the SonarQube from the catalog
```
Login using admin/admin then update the password. 

Open SonarQube and create new project, give it a name, generate a token and use them as parameters in our next CI/CD steps

<img width="546" alt="Screen Shot 2021-01-03 at 19 26 53" src="https://user-images.githubusercontent.com/18471537/103484717-abd52480-4df9-11eb-8295-f051dde06e5a.png">

Make sure to select Maven here.

## 4) Build Jenkins CI/CD using Jenkins File

Now create new pipeline for the project, where we checkout the code, run unit testing, run sonar qube analysis, build the application, get manual approval for deployment and finally deploy it on Openshift.  
Add this as pipeline script from SCM and populate it with our main branch in the git repository and cicd/jenkinsfile configurations. 

<img width="967" alt="Screen Shot 2021-02-09 at 16 22 54" src="https://user-images.githubusercontent.com/18471537/107381406-78bc3a00-6af7-11eb-926a-b22b26a237bd.png">

The jenkins file is in the folder cicd/jenkinsfile. 

As you can see this pipeline pick the maven slave image that we built, note the label must match what we configurd before:
```
agent {
    // Using the maven builder agent
       label "maven"
    }
```

The pipeline uses many parameters that will be instantiated with first execution (that will fail for that) and in subsequent execution the parameters will be available.  

To configure Slcak notification, you need to have a slack channel and add app to it, then enable incoming-webhooks for this app. Then use this webhook url in the parameter of this pipeline so you can get start, finish and failed notification to your slack channel.

<img width="674" alt="Screen Shot 2021-11-11 at 17 40 30" src="https://user-images.githubusercontent.com/18471537/141326117-d5d86cef-32a7-4545-90d5-2d37513dbb89.png">

These parameters are populated during the execution. 

<img width="1270" alt="Screen Shot 2021-01-03 at 15 47 50" src="https://user-images.githubusercontent.com/18471537/103480534-92be7a80-4ddd-11eb-96b2-23007d19c242.png">

This will make sure our project initally deployed and ready for our CI/CD configurations, where proj_name and app_name is Openshift project and application name respectively.

<img width="1452" alt="Screen Shot 2021-11-11 at 17 38 25" src="https://user-images.githubusercontent.com/18471537/141325863-63302d7a-b6bc-4895-a83d-2542afbe41b4.png">

Note that maven "verify" goal will check as configured in the pom.xml for the minimum test coverage and it will fail if not fullfilled this minimum required test coverage, it can be based on instructions or lines or classes, in our case we configured it to pass on 0.5 coverage otherwise it will fail the pipeline execution.
```
[INFO] Analyzed bundle 'demo' with 2 classes
[WARNING] Rule violated for package osa.ora.demo: lines covered ratio is 0.5, but expected minimum is 0.6
[INFO] [1m------------------------------------------------------------------------[m
[INFO] [1;31mBUILD FAILURE[m
```

## 5) Deployment Across Environments

Environments can be another Openshift project in the same Openshift cluster or in anither cluster.

In order to do this for the same cluster, you can enrich the pipeline and add approvals to deploy to a new project, all you need is to have the required privilages using "oc policy" as we did before and add deploy stage in the pipeline script to deploy into this project.

```
oc project {project_name} //this is new project to use
oc policy add-role-to-user edit system:serviceaccount:cicd:default -n {project_name}
oc policy add-role-to-user edit system:serviceaccount:cicd:jenkins -n {project_name}
```
Add more stages to the pipleine scripts like:
```
    stage('Deployment to Staging Approval') {
        steps {
            timeout(time: 5, unit: 'MINUTES') {
                input message: 'Proceed with Application Deployment in Staging environment ?', ok: 'Approve Deployment'
            }
        }
    }
    stage('Deploy To Openshift Staging') {
      steps {
        sh "oc project ${staging_proj_name}"
        sh "oc start-build ${app_name} --from-dir=."
        sh "oc logs -f bc/${app_name}"
      }
    }
```
You can use oc login command with different cluster to deploy the application into different clusters.
Also you can use Openshift plugin and configure different Openshift cluster to automated the deployments across many environments:

```
stage('preamble') {
	steps {
		script {
			openshift.withCluster() {
			//could be openshift.withCluster( 'another-cluster' ) {
				//name references a Jenkins cluster configuration
				openshift.withProject() { 
				//coulld be openshift.withProject( 'another-project' ) {
					//name references a project inside the cluster
					echo "Using project: ${openshift.project()} in cluster:  ${openshift.cluster()}"
				}
			}
		}
	}
}
```
And then configure any additonal cluster (other than the default one which running Jenkins) in Openshift Client plugin configuration section:

<img width="1400" alt="Screen Shot 2021-01-05 at 10 24 45" src="https://user-images.githubusercontent.com/18471537/103623100-4b043400-4f40-11eb-90cf-2209e9c4bde2.png">

## 6) Using Tekton Pipeline

Similar to what we did in Jenkins we can build the pipeline using TekTon. 
To do this we need to start by installing the SonarQube Tekton task and Slack notification task using the following commands:

```
oc project cicd
oc apply -f https://raw.githubusercontent.com/osa-ora/simple_java_maven/main/cicd/sonarqube-scanner-with-login-param.yaml -n cicd
oc apply -f https://raw.githubusercontent.com/tektoncd/catalog/main/task/send-to-webhook-slack/0.1/send-to-webhook-slack.yaml
```
Note that we can add the "sonar-project.properties" file in the project root which contains the sonar qube configurations as following. 
```
sonar.projectKey=maven
sonar.host.url=my_sonarqube_server
sonar.sources=src/main/java/
sonar.language=java
sonar.java.binaries=target/
sonar.login=908f6535388b143d5be06b21cae3229cb1d55054
```
To configure Slcak notification, you need to have a slack channel and add app to it, then enable incoming-webhooks for this app.
Then you need to create a secret in Openshift with this incoming-wehbook url:
```
echo "kind: Secret
apiVersion: v1
metadata:
  name: webhook-secret
stringData:
  url: https://hooks.slack.com/services/........{the complete slack webhook url}" | oc create -f -
```
The slack channel will use this secret to post in the channel (note that this is an optional task).

Then we can import the pipeline and grant the "pipeline" user edit rights on dev namespace to deploy the application there.
```
oc project cicd
oc apply -f https://raw.githubusercontent.com/osa-ora/simple_java_maven/main/cicd/tekton.yaml -n cicd
oc policy add-role-to-user edit system:serviceaccount:cicd:pipeline -n dev
```
The following graph shows the pipeline steps and flow:

<img width="1509" alt="Screen Shot 2021-11-11 at 15 34 59" src="https://user-images.githubusercontent.com/18471537/141307775-ecd209cd-76db-4494-8e31-508e6096f220.png">

You can now, start the pipeline and select the proper parameters and fill in the maven-workspace where the pipeline shared input/outputs

<img width="914" alt="Screen Shot 2021-10-26 at 10 02 59" src="https://user-images.githubusercontent.com/18471537/138834527-4ff847cb-ddc0-4af6-93f4-76715387e3ba.png">

Once the execution is completed, you will see the pipeline run output and logs and you can then access the deployed application:

With sucessful execution:
<img width="1495" alt="Screen Shot 2021-11-11 at 15 35 27" src="https://user-images.githubusercontent.com/18471537/141307848-2ca2f4e4-8f42-4800-b3c8-083594abc245.png">

With failed execution:
<img width="1487" alt="Screen Shot 2021-11-11 at 15 36 12" src="https://user-images.githubusercontent.com/18471537/141307944-c25a4291-d913-4d3b-9362-92cc2baaba33.png">

You will get slack notifications accordingly when the pipeline start the execution and at the end with the pipeline execution results, if you don't want to use it, you can just set the slack notification parameter in the pipeline as false. 

<img width="695" alt="Screen Shot 2021-11-11 at 15 39 04" src="https://user-images.githubusercontent.com/18471537/141307714-cf10cf01-47db-4120-96c1-282c4ddfeab3.png">

Note: We have used source2image task to deploy the application, but we could just use Openshift binary build (oc) for the generated jar file similar to what we did in Jenkins pipeline, but we used s2i task here for more demonstration of the available options.

We have modified the standard SonarQube task to accept the Sonar-login as a parameter in the file "cicd/sonarqube-scanner-with-login-param.yaml"
To illustrate this, the modified file has 2 main changes, one related to adding the new parameter and another one related to using the parameter for creating the file:
```
- name: SONAR_LOGIN
      description: Login Token
      default: ""  

...
...

else
  touch sonar-project.properties
  echo "sonar.projectKey=$(params.SONAR_PROJECT_KEY)" >> sonar-project.properties
  echo "sonar.host.url=$(params.SONAR_HOST_URL)" >> sonar-project.properties
  echo "sonar.sources=." >> sonar-project.properties
  echo "sonar.java.binaries=target/" >> sonar-project.properties
  echo "sonar.login=$(params.SONAR_LOGIN)" >> sonar-project.properties
fi

```
And as you can see in the following screenshot, the new parameter for the login token in the sonar scanner task is available.
<img width="1480" alt="Screen Shot 2021-10-26 at 11 18 14" src="https://user-images.githubusercontent.com/18471537/138849141-0216edff-eaa8-4cdb-a8fc-ccf23ef5305f.png">


Note this modification only works when the sonar-project.properties is not exist in the project root, otherwise you can modify it more if you want to override the existing token.

You can then use another alternative to the above approach as following:

Using Secrets for Login Token for SonarQube task
===================================
1) Removing the sonar-project.properties if exist
2) Modify the template to use the template that utilize the secret for login information
```
oc apply -f https://raw.githubusercontent.com/osa-ora/simple_java_maven/main/cicd/sonarqube-scanner-with-secret.yaml -n cicd
```
3) Create secret that contains the login token in the dev project:
```
kind: Secret
apiVersion: v1
metadata:
  name: sonarlogin
  namespace: dev
data:
  sonarlogin: {sonar_loging_token_here}
type: Opaque
```
Now you can execute the pipeline without having to worry about storing the login token in the git repository. 

To illustrate this, the modified file has 2 main changes:
One related to using the secret. 
```
stepTemplate:
    env:
    - name: SONAR_TOKEN
      valueFrom:
        secretKeyRef:
          name: sonarlogin
          key: sonarlogin
```
The other change related to create the sonar-project.propoerties with more fields. 
```
else
  touch sonar-project.properties
  echo "sonar.projectKey=$(params.SONAR_PROJECT_KEY)" >> sonar-project.properties
  echo "sonar.host.url=$(params.SONAR_HOST_URL)" >> sonar-project.properties
  echo "sonar.sources=." >> sonar-project.properties
  echo "sonar.java.binaries=target/" >> sonar-project.properties
fi
```

## 7) Using Nexus Repository Manager On OpenShift

One of the good options is to unify all your DevOps tools on OpenShift, one of them could be Nexus repository manager, to deploy it as simple as follow the following steps:
```
oc project cicd
oc new-app sonatype/nexus
oc expose svc/nexus
```
Now you can access the Nexus from the URL: ${route}/nexus then login using admin/admin123. 

<img width="956" alt="Screen Shot 2021-10-28 at 09 24 23" src="https://user-images.githubusercontent.com/18471537/139207013-c6defb0d-3169-4f8d-92a3-7adf9dab703c.png">

To use Nexus in pipeline you can then use the MAVEN_MIRROR_URL, it can be also used in source2image by using the -e param as following:  

```
oc new-build openshift/wildfly-100-centos7:latest~https://github.com/openshift/jee-ex.git \
	-e MAVEN_MIRROR_URL='http://nexus.<Nexus_Project>:8081/nexus/content/groups/public'
```

Note that you need to attach storage for Nexus to store the repositories and mount it into '/sonatype-work/' so it persist all the repositories there.  
You can follow the following steps that mount 4Gi of storage volume:
```
echo "apiVersion: v1
kind: PersistentVolumeClaim
metadata:
 name: nexus-pvc
spec:
 accessModes:
 - ReadWriteOnce
 resources:
   requests:
     storage: 4Gi" | oc create -f -
oc set volumes deployments/nexus
oc scale deployments/nexus --replicas=0 -n cicd
oc set volumes deployments/nexus --remove --name=nexus-volume-1 
oc set volumes deployments/nexus --add --name=nexus-data --mount-path=/sonatype-work/ --type persistentVolumeClaim --claim-name=nexus-pvc
oc scale deployments/nexus --replicas=1 -n cicd
oc set volumes deployments/nexus
```
Now all repository work will be persisted in the volume.

You can also use any of the following templates:
https://github.com/OpenShiftDemos/nexus

## 7) Using Gitea Repository On OpenShift

One of the good options is to unify all your DevOps tools on OpenShift, Gitea Repository, to deploy it as simple as follow the following steps:
```
oc new-project cicd
oc create serviceaccount gitea
oc adm policy add-scc-to-user anyuid system:serviceaccount:cicd:gitea
oc apply -f https://raw.githubusercontent.com/osa-ora/simple_java_maven/main/cicd/gitea-persistent-storageclass-param.yaml
```
Note: this template is a modification of this template: https://github.com/lathil/openshift-gitea/blob/master/templates/gitea-persistent.yaml to add a new parameter to allow us to configure the storage class, you can modifey it further more to mount an existing pvc. 

Now, we need to import the container image for Gitea from: docker.io/gitea/gitea:latest the easiest way is to use the Add --> Container Image from the Developer Console. Fill in the image details, blank application name, and 'gitea' as name, deselect the route and set replica count as zero. 

<img width="1086" alt="Screen Shot 2021-11-02 at 11 41 40" src="https://user-images.githubusercontent.com/18471537/139824630-c3c5b906-b9be-48fb-a94b-4bb105bb2121.png">

<img width="502" alt="Screen Shot 2021-11-02 at 11 42 28" src="https://user-images.githubusercontent.com/18471537/139824719-d053b303-1d21-4662-940f-63c977b8ed3a.png">


Once you click on create, the image wil be imported inside Openshift, you ca now delete the deployment configurations and service of it.

If you check the Image streams, you will find the image imported successfully.

<img width="937" alt="Screen Shot 2021-11-02 at 11 53 43" src="https://user-images.githubusercontent.com/18471537/139824874-75cd229a-f253-48a9-a056-c92c390183a9.png">

Now from the catalog use the newly created "Gitea" template to create your Gitea server:

<img width="595" alt="Screen Shot 2021-11-02 at 11 45 53" src="https://user-images.githubusercontent.com/18471537/139824969-a736f124-37aa-4c9e-b8b1-bfae36bec8cd.png">

Then populate the data correctly for the Storage class available in your OpenShift cluster and the required size:

<img width="628" alt="Screen Shot 2021-11-02 at 11 48 08" src="https://user-images.githubusercontent.com/18471537/139825052-d8a05545-b61c-4a64-846f-ce0bb4edc9e0.png">

Also configure the CPU, memroy and all other parameters, most important to configure the root URL (by Openshift URL or IP of worker-node:Node-Port)

<img width="645" alt="Screen Shot 2021-11-02 at 11 49 38" src="https://user-images.githubusercontent.com/18471537/139825190-92d11642-c375-4f0b-a6bc-34a0fa7c98c7.png">

Some of these configurations will be stored in the config map named 'gitea-config'

Now click on create button and once created, you can access the Gitea server using the route and configure it for the first time e.g. email setings and other first time configurations.

![image](https://user-images.githubusercontent.com/18471537/139826764-8aa73db2-2b5a-48bb-a310-04b4da3000cb.png)

Later you can register users and create repositories.

<img width="1769" alt="Screen Shot 2021-11-02 at 11 58 28" src="https://user-images.githubusercontent.com/18471537/139825591-77227d4d-1b17-4e77-aec2-98ba326eb751.png">













