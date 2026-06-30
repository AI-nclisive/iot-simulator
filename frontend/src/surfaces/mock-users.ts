export type UserRole = "admin" | "user";
export type UserStatus = "active" | "inactive";

export type UserRow = {
  id: string;
  name: string;
  email: string;
  role: UserRole;
  status: UserStatus;
  lastActive: string;
};

/** Set to true in tests to simulate a failed save response. */
export let mockUserSaveShouldFail = false;
export function setMockUserSaveShouldFail(v: boolean) {
  mockUserSaveShouldFail = v;
}

export const mockUsers: UserRow[] = [
  {
    id: "u-001",
    name: "Jordan Kim",
    email: "jordan.kim@example.com",
    role: "admin",
    status: "active",
    lastActive: "2 min ago",
  },
  {
    id: "u-002",
    name: "Alex Rivera",
    email: "alex.rivera@example.com",
    role: "user",
    status: "active",
    lastActive: "1 hour ago",
  },
  {
    id: "u-003",
    name: "Sam Chen",
    email: "sam.chen@example.com",
    role: "user",
    status: "active",
    lastActive: "3 hours ago",
  },
  {
    id: "u-004",
    name: "Taylor Brooks",
    email: "taylor.brooks@example.com",
    role: "user",
    status: "inactive",
    lastActive: "5 days ago",
  },
  {
    id: "u-005",
    name: "Morgan Lee",
    email: "morgan.lee@example.com",
    role: "admin",
    status: "active",
    lastActive: "Yesterday",
  },
  {
    id: "u-006",
    name: "Casey Davis",
    email: "casey.davis@example.com",
    role: "user",
    status: "inactive",
    lastActive: "2 weeks ago",
  },
  {
    id: "u-007",
    name: "Riley Wilson",
    email: "riley.wilson@example.com",
    role: "user",
    status: "active",
    lastActive: "30 min ago",
  },
];
