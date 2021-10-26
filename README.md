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
oc process -f https://raw.githubusercontent.com/osa-ora/simple_java_gradle/main/cicd/sonarqube-persistent-template.yaml | oc create -f -

Open SonarQube and create new project, give it a name, generate a token and use them as parameters in our next CI/CD steps

<img width="546" alt="Screen Shot 2021-01-03 at 19 26 53" src="https://user-images.githubusercontent.com/18471537/103484717-abd52480-4df9-11eb-8295-f051dde06e5a.png">

Make sure to select Maven here.

## 4) Build Jenkins CI/CD using Jenkins File

Now create new pipeline for the project, where we checkout the code, run unit testing, run sonar qube analysis, build the application, get manual approval for deployment and finally deploy it on Openshift.  
Add this as pipeline script from SCM and populate it with our main branch in the git repository and cicd/jenkinsfile configurations. 

<img width="967" alt="Screen Shot 2021-02-09 at 16 22 54" src="https://user-images.githubusercontent.com/18471537/107381406-78bc3a00-6af7-11eb-926a-b22b26a237bd.png">

Here is the content of the file: (in cicd folder/jenkinsfile)

```
// Maintaned by Osama Oransa
// First execution will fail as parameters won't populated
// Subsequent runs will succeed if you provide correct parameters
def firstDeployment = "No";
pipeline {
	options {
		// set a timeout of 20 minutes for this pipeline
		timeout(time: 20, unit: 'MINUTES')
	}
    agent {
    // Using the maven builder agent
       label "maven"
    }
  stages {
    stage('Setup Parameters') {
            steps {
                script { 
                    properties([
                        parameters([
                        choice(
                                choices: ['Yes', 'No'], 
                                name: 'runSonarQube',
                                description: 'Run Sonar Qube Analysis?'
                            ),
                        string(
                                defaultValue: 'dev', 
                                name: 'proj_name', 
                                trim: true,
                                description: 'Openshift Project Name'
                            ),
                        string(
                                defaultValue: 'maven-app', 
                                name: 'app_name', 
                                trim: true,
                                description: 'Maven Application Name'
                            ),
                        string(
                                defaultValue: 'https://github.com/osa-ora/simple_java_maven', 
                                name: 'git_url', 
                                trim: true,
                                description: 'Git Repository Location'
                            ),
                        string(
                                defaultValue: 'http://sonarqube-cicd.apps.cluster-894c.894c.sandbox1092.opentlc.com', 
                                name: 'sonarqube_url', 
                                trim: true,
                                description: 'Sonar Qube URL'
                            ),
                        string(
                                defaultValue: 'maven', 
                                name: 'sonarqube_proj', 
                                trim: true,
                                description: 'Sonar Qube Project Name'
                            ),
                        password(
                                defaultValue: '377789513f6eb33c20f760dba7f94331e4c29d52', 
                                name: 'sonarqube_token', 
                                description: 'Sonar Qube Token'
                            )
                        ])
                    ])
                }
            }
    }
    stage('Code Checkout') {
      steps {
        git branch: 'main', url: '${git_url}'
        sh "ls -l"
      }
    }
    stage('Unit Testing & Code Coverage') {
      steps {
        sh "mvn test"
        junit '**/TEST-*.xml'
        publishHTML([allowMissing: false, alwaysLinkToLastBuild: true, keepAll: false, reportDir: 'target/site/jacoco', reportFiles: 'index.html', reportName: 'Unit Test Code Coverage', reportTitles: 'Code Coverage'])
        sh 'mvn verify'
      }
    }
    stage('Code Scanning by Sonar Qube') {
        when {
            expression { runSonarQube == "Yes" }
        }
        steps {
            sh "mvn sonar:sonar -Dsonar.login=${sonarqube_token} -Dsonar.host.url=${sonarqube_url} -Dsonar.projectKey=${sonarqube_proj}"
        }
    }
    stage('Build Deployment Package'){
        steps{
            sh "mvn package"
            archiveArtifacts 'target/**/*.jar'
        }
    }
    stage('Deployment Approval') {
        steps {
            timeout(time: 5, unit: 'MINUTES') {
                input message: 'Proceed with Application Deployment?', ok: 'Approve Deployment'
            }
        }
    }
    stage("Check Deployment Status"){
        steps {
            script {
              try {
                    sh "oc get svc/${app_name} -n=${proj_name}"
                    sh "oc get bc/${app_name} -n=${proj_name}"
                    echo 'Already deployed, incremental deployment will be initiated!'
                    firstDeployment = "No";
              } catch (Exception ex) {
                    echo 'Not deployed, initial deployment will be initiated!'
                    firstDeployment = "Yes";
              }
            }
        }
    }
    stage('Initial Deploy To Openshift') {
        when {
            expression { firstDeployment == "Yes" }
        }
        steps {
            sh "oc new-build --image-stream=java:latest --binary=true --name=${app_name} -n=${proj_name}"
            sh "mkdir target/jar"
            sh "cp target/*.jar target/jar"
            sh "oc start-build ${app_name} --from-dir=target/jar -n=${proj_name}"
            sh "oc logs -f bc/${app_name} -n=${proj_name}"
            sh "oc new-app ${app_name} --as-deployment-config -n=${proj_name}"
            sh "oc expose svc ${app_name} --port=8080 --name=${app_name} -n=${proj_name}"
        }
    }
    stage('Incremental Deploy To Openshift') {
        when {
            expression { firstDeployment == "No" }
        }
        steps {
            sh "mkdir target/jar"
            sh "cp target/*.jar target/jar"
            sh "oc start-build ${app_name} --from-dir=target/jar -n=${proj_name}"
            sh "oc logs -f bc/${app_name} -n=${proj_name}"
        }
    }
    stage('Smoke Test') {
        steps {
            sleep(time:15,unit:"SECONDS")
            sh "curl \$(oc get route ${app_name} -n=${proj_name} -o jsonpath='{.spec.host}')/loyalty/v1/balance/123 | grep '123'"
        }
    }
  }
} // pipeline
```
As you can see this pipeline pick the maven slave image that we built, note the label must match what we configurd before:
```
agent {
    // Using the maven builder agent
       label "maven"
    }
```

The pipeline uses many parameters that will be instantiated with first execution (that will fail for that) and in subsequent execution the parameters will be available.

<img width="1270" alt="Screen Shot 2021-01-03 at 15 47 50" src="https://user-images.githubusercontent.com/18471537/103480534-92be7a80-4ddd-11eb-96b2-23007d19c242.png">

This will make sure our project initally deployed and ready for our CI/CD configurations, where proj_name and app_name is Openshift project and application name respectively.

<img width="1425" alt="Screen Shot 2021-01-28 at 13 01 16" src="https://user-images.githubusercontent.com/18471537/106132620-d7cf9580-616c-11eb-8dca-b3782320a436.png">

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
To do this we need to start by installing the SonarQube Tekton task using:

```
oc apply -f https://raw.githubusercontent.com/tektoncd/catalog/main/task/sonarqube-scanner/0.1/sonarqube-scanner.yaml -n dev
```
Note that we have added the sonar-project.properties file in the project root which contains the sonar qube configurations as following. 
```
sonar.projectKey=maven
sonar.host.url=my_sonarqube_server
sonar.sources=src/main/java/
sonar.language=java
sonar.java.binaries=target/
sonar.login=908f6535388b143d5be06b21cae3229cb1d55054
```
To install SonarQube on OpenShift and update the sonar-project.properties refer to step no#3 above.  
Then we can import the pipeline
```
oc apply -f https://raw.githubusercontent.com/osa-ora/simple_java_maven/main/cicd/tekton.yaml -n dev
```
The following graph shows the pipeline steps and flow:

<img width="1094" alt="Screen Shot 2021-10-26 at 09 55 59" src="https://user-images.githubusercontent.com/18471537/138834232-393ecf93-ac67-4494-93a1-0278842de491.png">

You can now, start the pipeline and select the proper parameters and fill in the maven-workspace where the pipeline shared input/outputs

<img width="914" alt="Screen Shot 2021-10-26 at 10 02 59" src="https://user-images.githubusercontent.com/18471537/138834527-4ff847cb-ddc0-4af6-93f4-76715387e3ba.png">

Once the execution is completed, you will see the pipeline run output and logs and you can then access the deployed application:

<img width="1094" alt="Screen Shot 2021-10-26 at 09 56 35" src="https://user-images.githubusercontent.com/18471537/138835188-24a69f28-7a50-4fb8-8012-8e865d0a70cf.png">

Note: the current SonarQube task is not accepting the Sonar-login as a parameter, you can then use another alternative to the above approach as following:

First Approach: Using Secrets for Login Token
===============================
1) Removing the sonar-project.properties
2) Modify the template to use the template that utilize the secret for login information
```
oc apply -f https://raw.githubusercontent.com/osa-ora/simple_java_maven/main/cicd/sonarqube-scanner-with-secret.yaml -n dev
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
  echo "sonar.sources=src/main/java/" >> sonar-project.properties
  echo "sonar.java.binaries=target/" >> sonar-project.properties
fi
```
Second Approach: Using Pipeline Params for Login Token
========================================
1) Removing the sonar-project.properties
2) Modify the template to use the template that utilize the parameter for login information
```
oc apply -f https://raw.githubusercontent.com/osa-ora/simple_java_maven/main/cicd/sonarqube-scanner-with-login-param.yaml -n dev
```
3) Add pipeline parameter and start the pipeline

<img width="1480" alt="Screen Shot 2021-10-26 at 11 18 14" src="https://user-images.githubusercontent.com/18471537/138849141-0216edff-eaa8-4cdb-a8fc-ccf23ef5305f.png">

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
  echo "sonar.sources=src/main/java/" >> sonar-project.properties
  echo "sonar.java.binaries=target/" >> sonar-project.properties
  echo "sonar.login=$(params.SONAR_LOGIN)" >> sonar-project.properties
fi

```




