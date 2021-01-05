# Openshift CI/CD for simple Java SpringBoot App with Maven

This Project Handle the basic CI/CD of Java application using Maven   

To run locally with Maven installed:   

```
mvn test // to run the unit tests
mvn clean test spring-boot:run // to build, test and run the application
mvn package //to build the jar file of this SpringBoot app

```

To use Jenkins on Openshift for CI/CD, Openshift come with Jenkins Maven Slave template to use in our CI/CD 

## 1) Build The Environment

You can run use the build :  

```
oc project cicd //this is the project for cicd
oc project dev //this is project for application development

oc new-app jenkins-persistent  -n cicd
oc policy add-role-to-user edit system:serviceaccount:cicd:default -n dev
oc policy add-role-to-user edit system:serviceaccount:cicd:jenkins -n dev
```


## 2) Configure Jenkins 
No need as Jenkins already come with pre-configured maven slave image.
From inside Jenkins --> go to Manage Jenkins ==> Configure Jenkins then scroll to cloud section:
https://{JENKINS_URL}/configureClouds
Now click on Pod Templates, Check the template named "maven"

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
Here is the content of the file:

```
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
    stage('Checkout') {
      steps {
        git branch: 'main', url: '${git_url}'
        sh "ls -l"
      }
    }
    stage('Unit Testing') {
      steps {
        sh "mvn test"
      }
    }
    stage('Sonar Qube') {
      steps {
        sh "mvn sonar:sonar -Dsonar.login=${sonarqube_token} -Dsonar.host.url=${sonarqube_url} -Dsonar.projectKey=${sonarqube_proj}"
      }
    }
    stage('Build App'){
        steps{
            sh "mvn package"
        }
    }
    stage('Deployment Approval') {
        steps {
            timeout(time: 5, unit: 'MINUTES') {
                input message: 'Proceed with Application Deployment?', ok: 'Approve Deployment'
            }
        }
    }
    stage('Deploy To Openshift') {
      steps {
        sh "oc project ${proj_name}"
        sh "oc start-build ${app_name} --from-dir=target/."
        sh "oc logs -f bc/${app_name}"
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

The pipeline uses many parameters:
```
- String parameter: proj_name //this is Openshift project for the application
- String parameter: app_name //this is the application name
- String parameter: git_url //this is the git url of our project, default is hhttps://github.com/osa-ora/simple_java_maven
- String parameter: sonarqube_url: //this is the sonarqube url in case it is used for code scanning
- String parameter: sonarqube_token //this is the token 
- String parameter: sonarqube_proj // the project name in sonarqube
```

<img width="1270" alt="Screen Shot 2021-01-03 at 15 47 50" src="https://user-images.githubusercontent.com/18471537/103480534-92be7a80-4ddd-11eb-96b2-23007d19c242.png">

The project assume that you already build and deployed the application before, so we need to have a Jenkins freestyle project where we initally execute the following commands in Jenkins after checkout the code:
```
mvn package
oc project ${proj_name}
oc new-build --image-stream=java:latest --binary=true --name=${app_name}
oc start-build ${app_name} --from-dir=target/
oc logs -f bc/${app_name}
oc new-app ${app_name} --as-deployment-config
oc expose svc ${app_name} --port=8080 --name=${app_name}
```
This will make sure our project initally deployed and ready for our CI/CD configurations, where proj_name and app_name is Openshift project and application name respectively.

<img width="1036" alt="Screen Shot 2021-01-03 at 18 46 19" src="https://user-images.githubusercontent.com/18471537/103484674-60bb1180-4df9-11eb-8ec0-c885563301d1.png">

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



