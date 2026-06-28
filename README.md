# Pracisos Clinic Management System

Pracisos is a modern, microservices-based SaaS (Software as a Service) clinic management platform designed for healthcare providers. The system features a multi-tenant architecture, allowing a single deployment to securely and independently serve multiple clinics (tenants) with dedicated databases and event-driven data synchronization.

---

## 🚀 Features & Capabilities

### 1. Multi-Tenancy
* **Secure Tenant Isolation:** Multi-tenancy is enforced at the database level. Data is partitioned per tenant using `tenant_id` scopes.
* **Platform Admin Dashboard:** A central control panel for system administrators (`SYSTEM_ADMIN`) to register and provision new clinic tenants.
* **Tenant-Aware Routing:** The frontend dynamically routes users to their specific clinic portal (e.g., `/:tenantSlug/dashboard`) upon login.

### 2. Appointment Booking & Availability
* **Pessimistic Slot Locking:** Prevents double-bookings by acquiring database-level write locks (`PESSIMISTIC_WRITE`) on available slots during the booking transaction.
* **Availability Templates:** Practitioners can set recurring weekly availability templates, which automatically generate concrete, bookable time slots.
* **Real-Time Booking Flow:** A responsive, patient-facing UI for selecting a practitioner, viewing available slots on a 7-day calendar, and confirming bookings.

### 3. Event-Driven Architecture
* **Kafka Synchronization:** User registration and tenant creation events are published by the `auth-service` and consumed asynchronously by the `booking`, `charting`, and `billing` services to keep their local caches in sync.

---

## 🛠️ Technology Stack

### Backend Services
* **Core:** Java 21, Spring Boot 3.3.x, Spring Security (JWT-based authentication)
* **Data Access:** Spring Data JPA, Hibernate, PostgreSQL (separate databases per service)
* **Database Migrations:** Flyway (manages schema creation and seeding)
* **Messaging:** Apache Kafka (event streaming and asynchronous communication)
* **Build Tool:** Maven

### Frontend Client
* **Framework:** React 19, TypeScript, Vite
* **State Management & API:** Redux Toolkit, RTK Query (auto-caching and query synchronization)
* **Styling:** Tailwind CSS (utility-first styling), Lucide React (icons)
* **Web Server:** Nginx (serves static assets and acts as a reverse proxy for API routing)

### Testing & Infrastructure
* **E2E Testing:** Playwright (headless browser testing)
* **Containerization:** Docker, Docker Compose

---

## 🏗️ Architecture & Services

The system is split into the following services, orchestrated via Docker Compose:

| Service | Port | Description |
| :--- | :--- | :--- |
| **`frontend`** | `3000` | React SPA served by Nginx. Proxies `/api/v1/*` requests to the respective backend services. |
| **`auth-service`** | `8080` | Manages authentication, JWT generation, user accounts, and tenant creation. |
| **`booking-service`** | `8081` | Manages practitioners, availability, slots, and appointments. |
| **`charting-service`** | `8082` | Handles clinical charting (standalone). |
| **`billing-service`** | `8083` | Handles invoices and billing (standalone). |
| **`postgres-db`** | `5432` | Dedicated database containers for each service (`auth_db`, `booking_db`, etc.). |
| **`kafka` / `zookeeper`** | `9092` | Manages event streaming and messaging. |

---

## 🏁 Getting Started

### Prerequisites
* Docker & Docker Compose
* Node.js & npm (for running E2E tests locally)
* Maven & JDK 21 (for building Java services locally)

### Running the Application
To build and start all services in Docker:

```bash
# From the project root directory
sudo docker compose up -d --build
```

This will spin up the databases, run Flyway migrations (with seeded default clinic data), start the Kafka broker, build the Spring Boot jars, compile the React frontend, and launch the Nginx gateway.

### Accessing the Portals
* **Platform Admin Dashboard:** `http://localhost:3000/admin/dashboard`
  * *Credentials:* `admin@pracisos.com` / `admin123`
* **Clinic Portal (Default Tenant):** `http://localhost:3000/maple-health/dashboard`
  * *Owner Credentials:* `owner@maple-health.com` / `admin123`
  * *Practitioner Credentials:* `dr.bob@maple-health.com` / `admin123`
  * *Patient Credentials:* `patient@maple-health.com` / `admin123`

---

## 🧪 Running E2E Tests

The end-to-end tests are written in Playwright and verify authentication, tenant creation, and the complete appointment booking flow.

To run the tests:

```bash
# Navigate to the frontend directory
cd frontend

# Install Playwright browsers (if running for the first time)
npx playwright install

# Run the tests headlessly
PLAYWRIGHT_TEST_BASE_URL=http://localhost:3000 npx playwright test

# Run the tests in headed mode (to watch the browser perform the actions)
PLAYWRIGHT_TEST_BASE_URL=http://localhost:3000 npx playwright test --headed
```

---

## 🔮 Future Roadmap & Enhancements

### Phase 5: Onboarding & User Management
* **Automated Invitations:** Implement an email invitation service (SMTP/SES) and a token-based password activation flow. This will allow the `SYSTEM_ADMIN` to register a new tenant and automatically invite the clinic's initial `CLINIC_OWNER`.
* **Tenant Registration UI:** Add a user registration and invitation management UI in the Admin Control panel.

### Phase 6: Production Orchestration with Kubernetes
* **Transition to Kubernetes (K8s):** Transition the infrastructure from Docker Compose to a Kubernetes cluster for production-grade deployment.
* **Scalability & High Availability:** Leverage K8s Deployments, Services, and Ingress resources to manage microservice replica scaling, rolling updates, and load balancing.
* **Secret & Config Management:** Use Kubernetes Secrets and ConfigMaps to securely externalize database credentials, Kafka brokers, and JWT private keys.
