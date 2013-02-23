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

public class BudgetAccount {
	private String accountId;
	private Double budget;
	
	public String getAccountId() {
		return accountId;
	}
	
	public void setAccountId(String accountId) {
		this.accountId = accountId;
	}
	
	public Double getBudget() {
		return budget;
	}
	
	public void setBudget(Double budget) {
		this.budget = budget;
	}
	
	@Override
	public String toString() {
		return "BudgetAccount [accountId=" + accountId + ", budget=" + budget + "]";
	}
}
