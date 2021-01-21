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

Provision Jenkins and add the required privilages to dpeloy into different projects :  

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
Here is the content of the file: (in cicd folder/jenkinsfile)

```
// Maintaned by Osama Oransa
// First execution will fail as parameters won't populated
// Subsequent runs will succeed if you provide correct parameters
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
                                choices: ['No', 'Yes'], 
                                name: 'firstDeployment',
                                description: 'First Deployment?'
                            ),
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
        archiveArtifacts '**/TEST-*.xml'
        archiveArtifacts 'target/site/jacoco//**/*.*'
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
    stage('Initial Deploy To Openshift') {
        when {
            expression { firstDeployment == "Yes" }
        }
        steps {
            sh "oc project ${proj_name}"
            sh "oc new-build --image-stream=java:latest --binary=true --name=${app_name}"
            sh "mkdir target/jar"
            sh "cp target/*.jar target/jar"
            sh "oc start-build ${app_name} --from-dir=target/jar"
            sh "oc logs -f bc/${app_name}"
            sh "oc new-app ${app_name} --as-deployment-config"
            sh "oc expose svc ${app_name} --port=8080 --name=${app_name}"
        }
    }
    stage('Incremental Deploy To Openshift') {
        when {
            expression { firstDeployment == "No" }
        }
        steps {
            sh "oc project ${proj_name}"
            sh "mkdir target/jar"
            sh "cp target/*.jar target/jar"
            sh "oc start-build ${app_name} --from-dir=target/jar"
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

The pipeline uses many parameters that will be instantiated with first execution (that will fail for that) and in subsequent execution the parameters will be available.

<img width="1270" alt="Screen Shot 2021-01-03 at 15 47 50" src="https://user-images.githubusercontent.com/18471537/103480534-92be7a80-4ddd-11eb-96b2-23007d19c242.png">

This will make sure our project initally deployed and ready for our CI/CD configurations, where proj_name and app_name is Openshift project and application name respectively.

<img width="1294" alt="Screen Shot 2021-01-20 at 22 15 00" src="https://user-images.githubusercontent.com/18471537/105317353-c7e30f00-5bca-11eb-8033-887d1c9ef6b6.png">

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






