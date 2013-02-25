/*
 * Copyright (c) 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chimpler.example.hazelcast;

import java.util.Collection;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.ClasspathPropertiesFileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.GroupIdentifier;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.hazelcast.client.ClientConfig;
import com.hazelcast.client.HazelcastClient;
import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.EntryListener;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.Member;
import com.hazelcast.query.SqlPredicate;

public class PersonClient {

	private final static String[] firstNames = {
		"Jacob", "Mason", "William", "Jayden", "Noah",
		"Michael", "Ethan", "Alexander", "Aiden", 
		"Daniel", "Sophia", "Isabella", "Emma",
		"Olivia", "Ava", "Emily", "Abigail", "Madison",
		"Mia", "Chloe"
	};
	private final static String[] lastNames = {
		"Smith", "Johnson", "Williams", "Jones",
		"Brown", "David", "Miller", "Wilson",
		"Moore", "Taylor", "Anderson", "Thomas",
		"Jackson", "White", "Harris", "Martin",
		"Thomson"
	};

	private final static String[] companyNames = {
		"CHOAM", "Acme Corp.", "Sirius Cybernetics Corp",
		"MomCorp", "Rick Industries", "Soylent Corp.",
		"Very Big Corp. of America", "Frobozz Magic Co.",
		"Warbuck Industries", "Tyrell Corp",
		"Wayne Enterprises", "VirtuCon", "Globex",
		"Umbrela Corp", "Wonka Industries"
	};
	
	private final static String[] streetNames = {
		"Second", "Third", "First",
		"Fourth", "Park", "Fifth",
		"Main", "Sixth", "Oak",
		"Seventh", "Pine", "Maple","Cedar"
	};

	private final static String[][] cities = {
		{"New York", "NY"}, {"Los Angeles", "CA"}, {"Chicago", "IL"},
		{"Houston", "TX"}, {"Philadelphia", "PA"}, {"Phoenix", "AZ"},
		{"San Antonia", "TX"}, {"San Diego", "CA"}, {"Dallas", "TX"}
	};
	
	public static void printHelp() {
		System.out.println("Arguments:");
		System.out.println("	add-random-data [count]: add random persons in the cache");
		System.out.println("	query [query]: run query");
		System.out.println("	get [ssn]: get person by ssn");
		System.out.println("	get-all: get all persons");
	}

	public static HazelcastInstance initHazelcastClient() throws Exception {
		ClientConfig hazelCastClientConfig = new ClientConfig();
		hazelCastClientConfig.getGroupConfig().setName("dev").setPassword("dev-pass");
		
		AWSCredentialsProvider awsCredentialProvider = new ClasspathPropertiesFileCredentialsProvider("aws.properties");
		AmazonEC2Client ec2 = new AmazonEC2Client(awsCredentialProvider);
        DescribeAvailabilityZonesResult availabilityZonesResult = ec2.describeAvailabilityZones();
        System.out.println("You have access to " + availabilityZonesResult.getAvailabilityZones().size() +
        		" Availability Zones.");

        DescribeInstancesResult describeInstancesRequest = ec2.describeInstances();

        for (Reservation reservation: describeInstancesRequest.getReservations()) {
        	for(Instance instance: reservation.getInstances()) {
        		for(GroupIdentifier group: instance.getSecurityGroups()) {
        			if (group.getGroupName().equals("jclouds#hazelcast")) {
                        System.out.println("EC2 instance " + instance.getPublicIpAddress());
                		hazelCastClientConfig.addAddress(instance.getPublicIpAddress(),
                				instance.getPublicIpAddress() + ":5701");
        			}
        		}
        	}
        }		
        HazelcastInstance hazelCastClient = HazelcastClient.newHazelcastClient(hazelCastClientConfig);
        
        return hazelCastClient;
	}

	protected static Address generateAddress() {
		Random random = new Random();
		Address address = new Address();
		address.setLine(String.format("%d %s street", 
				random.nextInt(2000),
				streetNames[random.nextInt(streetNames.length)]));
		String[] cityAndState = cities[random.nextInt(cities.length)];
		address.setCity(cityAndState[0]);
		address.setState(cityAndState[1]);
		return address;
	}
	
	public static void addRandomPersons(IMap<String, Person> personMap, int count) {
		Random random = new Random();
		
		for(int i = 0 ; i < count ; i++) {
			Person person = new Person();		
			person.setSsn(String.format("%09d", random.nextInt(1000000000)));
			person.setFirstName(firstNames[random.nextInt(firstNames.length)]);
			person.setLastName(lastNames[random.nextInt(lastNames.length)]);
			person.setAddress(generateAddress());
			person.setAge(random.nextInt(100));
			
			Company company = new Company();
			company.setName(companyNames[random.nextInt(companyNames.length)]);
			company.setAddress(generateAddress());
			person.setCompany(company);
			
			personMap.put(person.getSsn(), person);
			System.out.println("Add person " + person);
		}
	}
	
	public static void getPerson(IMap<String, Person> personMap, String ssn) {
		Person person = personMap.get(ssn);
		System.out.println("Person with ssn " + ssn + ": " + person);
	}

	public static void getAllPersons(IMap<String, Person> personMap) {
		Collection<Person> persons = personMap.values();
		for(Person person: persons) {
			System.out.println(person);
		}
	}

	public static void runQuery(IMap<String, Person> personMap, String query) {
		System.out.println("Persons matching predicate: " + query );
		Collection<Person> persons = personMap.values(
				new SqlPredicate(query)
		);
		for(Person person: persons) {
			System.out.println(person);
		}
	}

	public static void main(String args[]) throws Exception {
		if (args.length < 1) {
			printHelp();
			return;
		}
		String action = args[0];
		String query = null;
		String ssn = null;
		int count = 0;
		if (action.equals("add-random-data")) {
			count = Integer.parseInt(args[1]);
		} else if (action.equals("get-all")
				|| action.equals("add-indexes")) {
			// no extra argument expected
		} else if (action.equals("query")) {
			query = args[1]; 
		} else if (!action.equals("get")) {
			ssn = args[1]; 
		} else {
			System.out.println("Invalid action");
			printHelp();
			return;
		}

		HazelcastInstance instance = initHazelcastClient();
		IMap<String, Person> personMap = instance.getMap("person");
		
		try {
			if (action.equals("add-random-data")) {
				addRandomPersons(personMap, count);
			} else if (action.equals("get")) {
				getPerson(personMap, ssn);
			} else if (action.equals("get-all")) {
				getAllPersons(personMap);
			} else if (action.equals("query")) {
				runQuery(personMap, query);
			}
			instance.getLifecycleService().shutdown();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
}
