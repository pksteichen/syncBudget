// Simulated test category list modeled after a typical BudgeTrak user.
// The real app passes vm.categories.filter { !deleted } with the user's actual
// ids/names/tags. This list is only for the harness — it lets us measure how
// well the model picks a plausible category from a realistic set.

export const TEST_CATEGORIES = [
  { id: 1,  name: "Groceries",       tag: "" },
  { id: 2,  name: "Dining Out",      tag: "restaurants" },
  { id: 3,  name: "Fast Food",       tag: "" },
  { id: 4,  name: "Coffee",          tag: "" },
  { id: 5,  name: "Gas",             tag: "fuel" },
  { id: 6,  name: "Auto Maintenance",tag: "" },
  { id: 7,  name: "Public Transit",  tag: "" },
  { id: 8,  name: "Rideshare",       tag: "uber lyft" },
  { id: 9,  name: "Entertainment",   tag: "" },
  { id: 10, name: "Streaming",       tag: "subscriptions" },
  { id: 11, name: "Household",       tag: "target walmart" },
  { id: 12, name: "Hardware",        tag: "home depot lowes" },
  { id: 13, name: "Clothing",        tag: "" },
  { id: 14, name: "Personal Care",   tag: "pharmacy toiletries" },
  { id: 15, name: "Health",          tag: "medical copay" },
  { id: 16, name: "Pets",            tag: "" },
  { id: 17, name: "Kids",            tag: "school supplies" },
  { id: 18, name: "Gifts",           tag: "" },
  { id: 19, name: "Charity",         tag: "" },
  { id: 20, name: "Travel",          tag: "hotel airfare" },
  { id: 21, name: "Office",          tag: "work supplies" },
  { id: 22, name: "Utilities",       tag: "" },
];
