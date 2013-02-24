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

public class Client {

	
	public static void printHelp() {
		System.out.println("Arguments:");
		System.out.println("	add-money [accountId] [amount]: add money to the account. Set to negative to withdraw money");
		System.out.println("	continuous-spend [accountId]: spend money at random on the account");
		System.out.println("	list-accounts: list all accounts");
		System.out.println("	list-members: list all members");
		System.out.println("	listen: listen to changes to the account");
	}
	
	public static void listAccountBudgets(IMap<String, BudgetAccount> budgetAccountMap) {
		System.out.println("Budgets:");
		for(BudgetAccount account: budgetAccountMap.values()) {
			System.out.println(account);
		}
	}

	public static void addMoney(IMap<String, BudgetAccount> budgetAccountMap, String accountId, Double amount) throws Exception{
		// this is not optimal to use a lock but uses it to keep it simple
		budgetAccountMap.lock(accountId);
		try {
			BudgetAccount updatedAccount = new BudgetAccount();
			updatedAccount.setAccountId(accountId);
			
			BudgetAccount existingAccount = budgetAccountMap.get(accountId);
			if (existingAccount != null) {
				if (existingAccount.getBudget() < amount) {
					throw new Exception("Not enough money on the account");
				}
				updatedAccount.setBudget(existingAccount.getBudget() + amount);
			} else {
				if (amount < 0) {
					throw new Exception("Not enough money on the account");
				}
				updatedAccount.setBudget(amount);
			}
			budgetAccountMap.put(accountId, updatedAccount);
		} finally {
			budgetAccountMap.unlock(accountId);
		}
		System.out.println("Added " + amount + " to account " + accountId);
	}
	
	public static void listenAccountBudgets(IMap<String, BudgetAccount> budgetAccountMap) {
		budgetAccountMap.addEntryListener(new EntryListener<String, BudgetAccount>() {
			
			@Override
			public void entryUpdated(EntryEvent<String, BudgetAccount> budgetAccount) {
				System.out.println("Updated: " + budgetAccount);
			}
			
			@Override
			public void entryRemoved(EntryEvent<String, BudgetAccount> budgetAccount) {
				System.out.println("Removed: " + budgetAccount);
			}
			
			@Override
			public void entryEvicted(EntryEvent<String, BudgetAccount> budgetAccount) {
				System.out.println("Evicted: " + budgetAccount);
			}
			
			@Override
			public void entryAdded(EntryEvent<String, BudgetAccount> budgetAccount) {
				System.out.println("Added: " + budgetAccount);
			}
		}, true);
	}
	
	public static void listMembers(HazelcastInstance instance) {
		for(Member member: instance.getCluster().getMembers()) {
			System.out.println(member);
		}
	}
	
	public static void continuousSpend(final IMap<String, BudgetAccount> budgetAccountMap, final String accountId) throws Exception {
		ExecutorService executorService = Executors.newFixedThreadPool(10);
		for(int i = 0 ; i < 10 ; i++) {
			executorService.submit(new Runnable() {
				@Override
				public void run() {
					Random random = new Random();
					try {
						while(true) {
							addMoney(budgetAccountMap, accountId, -random.nextDouble());
						}
					} catch (Exception e) {
					}
				}
			});
		}
		executorService.shutdown();
		executorService.awaitTermination(5, TimeUnit.MINUTES);
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

	
	public static void main(String args[]) throws Exception {
		if (args.length < 1) {
			printHelp();
			return;
		}
		String action = args[0];
		String accountId = null;
		Double amount = null;
		if (action.equals("add-money")) {
			accountId = args[1]; 
			amount = Double.parseDouble(args[2]);
		} else if (action.equals("continuous-spend")) {
			accountId = args[1]; 
		} else if (!action.equals("list-accounts")
			&& !action.equals("list-members")
			&& !action.equals("listen")) {
			System.out.println("Invalid action");
			printHelp();
			return;
		}

		HazelcastInstance instance = initHazelcastClient();
		IMap<String, BudgetAccount> budgetAccountMap = instance.getMap("budget-account");
		try {
			if (action.equals("list-accounts")) {
				listAccountBudgets(budgetAccountMap);
				instance.getLifecycleService().shutdown();
			} else if (action.equals("list-members")) {
				listMembers(instance);
				instance.getLifecycleService().shutdown();
			} else if (action.equals("add-money")) {
				addMoney(budgetAccountMap, accountId, amount);
				instance.getLifecycleService().shutdown();
			} else if (action.equals("continuous-spend")) {
				continuousSpend(budgetAccountMap, accountId);
				instance.getLifecycleService().shutdown();
			} else if (action.equals("listen")) {
				listenAccountBudgets(budgetAccountMap);
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
}
