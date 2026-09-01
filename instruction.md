# Product Requirement Document: Multi-Engine Management & Auditing System

## 1. Overview
A web-based dashboard for managing remote order engines (e.g., BPL, PCL). It allows system administrators, admins, and users to start, stop, and monitor engines, view engine log outputs via remote scripts, and track system audit trails.

## 2. Tech Stack Recommendations
- **Frontend & Backend**: Next.js (App Router), TypeScript, Tailwind CSS, Shadcn UI
- **Database**: SQLite (via Prisma ORM) or PostgreSQL for simplicity
- **Authentication**: NextAuth.js / Lucia Auth (JWT-based session)
- **Engine Runner**: Node `ssh2` library to execute start/stop/log scripts on remote engine servers via SSH.

## 3. Role-Based Access Control (RBAC) Architecture

| Feature / Action | Sys.Admin | Admin | Regular User |
| :--- | :--- | :--- | :--- |
| View User List | ✅ Yes | ✅ Yes | ❌ No |
| Create / Delete Regular Users | ✅ Yes | ✅ Yes | ❌ No |
| Create / Delete Admin Users | ✅ Yes | ❌ No | ❌ No |
| Assign Roles/Engine Access to Users | ✅ Yes | ✅ Yes | ❌ No |
| Add / Delete Engines | ✅ Yes | ❌ No | ❌ No |
| Configure Engine SSH & Scripts | ✅ Yes | ❌ No | ❌ No |
| View Engines | ✅ All | ✅ All | 🔒 Only Assigned Engines |
| Start / Stop Engines | ✅ All | ✅ All | 🔒 Only Assigned Engines |
| View Audit Logs | ✅ Full System | ✅ Full System | 🔒 Assigned Engines Only |
| View Real-time Engine Logs | ✅ All | ✅ All | 🔒 Assigned Engines Only |

*Note: Roles correspond directly to Engine Access names (e.g., Role "BPL" grants access to the "BPL" engine). Users can have multiple roles.*

## 4. Data Models

### User
- `id`: String (UUID)
- `username`: String (Unique)
- `passwordHash`: String
- `roleType`: Enum (`SYS_ADMIN`, `ADMIN`, `USER`)
- `assignedRoles`: List of Engine Roles (e.g., `["BPL", "PCL"]`)

### Engine
- `id`: String (UUID)
- `name`: String (e.g., "BPL Order Engine")
- `code`: String (Unique, e.g., "BPL")
- `serverIp`: String
- `serverUsername`: String
- `serverPassword`: String (Encrypted)
- `startScript`: String (e.g., `systemctl start bpl-engine` or `./start.sh`)
- `stopScript`: String (e.g., `systemctl stop bpl-engine` or `./stop.sh`)
- `logScript`: String (e.g., `tail -n 100 /var/log/bpl.log`)
- `status`: Enum (`RUNNING`, `STOPPED`, `UNKNOWN`)

### AuditLog
- `id`: String (UUID)
- `timestamp`: DateTime
- `actorUsername`: String
- `actorRole`: String
- `action`: String (e.g., `START_ENGINE`, `STOP_ENGINE`, `ROLE_ASSIGNMENT`, `CREATE_USER`)
- `targetEngine`: String (Optional)
- `details`: String

## 5. UI Requirements
1. **Engine Dashboard**: Cards showing engine status (Running/Stopped), Start/Stop buttons, and "View Logs" modal/drawer.
2. **Logs Page**:
   - Filter dropdown by Engine.
   - Filter dropdown between **System Audit Logs** (actions taken by users/admins) and **Engine Execution Logs** (output from remote scripts).
3. **Admin Panel**:
   - User Management table (Create/Delete users, assign roles).
   - Engine Management modal (Sys.Admin only: input IP, Credentials, and custom Start/Stop/Log SSH scripts).