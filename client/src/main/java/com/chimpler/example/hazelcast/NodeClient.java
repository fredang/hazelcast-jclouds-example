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

public class NodeClient {
	public static HazelcastInstance initHazelcastClient() {
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
	
	public static void printHelp() {
		System.out.println("Arguments:");
		System.out.println("	add-money [accountId] [amount]: add money to the account. Set to negative to withdraw money");
		System.out.println("	listen: listen to changes to the account");
	}
	
	public static void main(String args[]) {
		HazelcastInstance instance = initHazelcastClient();
		IMap<String, BudgetAccount> budgetAccountMap = instance.getMap("budget-account");
		
		System.out.println("Budgets:");
		for(BudgetAccount account: budgetAccountMap.values()) {
			System.out.println(account);
		}
		
		if (args.length < 1) {
			printHelp();
			return;
		}
		
		String action = args[0];
		if (action.equals("add-money")) {
			if (args.length < 3) {
				printHelp();
				return;
			} 
			
			String accountId = args[1];
			Double amount = Double.parseDouble(args[2]);

			// this is not optimal to use a lock but uses it to keep it simple
			budgetAccountMap.lock(accountId);
			try {
				BudgetAccount updatedAccount = new BudgetAccount();
				updatedAccount.setAccountId(accountId);
				updatedAccount.setBudget(budgetAccountMap.get(accountId).getBudget() + amount);
				budgetAccountMap.put(accountId, updatedAccount);
			} finally {
				budgetAccountMap.unlock(accountId);
			}
			System.out.println("Added " + amount + " to account " + accountId);
		} else if (action.equals("listen")) {
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
		} else {
			printHelp();
			return;
		}
		
	}
}
