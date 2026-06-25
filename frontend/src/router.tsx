import { Navigate, createBrowserRouter } from "react-router-dom";
import { AppShell } from "./shell/app-shell";
import { CreateDataSourceWizardPage } from "./surfaces/create-data-source-wizard-page";
import { DataSourceDetailPreviewPage } from "./surfaces/data-source-detail-preview-page";
import { DataSourcesListPage } from "./surfaces/data-sources-list-page";
import { EvidenceListPage } from "./surfaces/evidence-list-page";
import { NotificationDemoPage } from "./surfaces/notification-demo-page";
import { OverviewPage } from "./surfaces/overview-page";
import { ProjectEntryPage } from "./surfaces/project-entry-page";
import { RecordingFlowPage } from "./surfaces/recording-flow-page";
import { ReplayFlowPage } from "./surfaces/replay-flow-page";
import { SettingsPage } from "./surfaces/settings-page";
import { SurfaceStubPage } from "./surfaces/surface-stub-page";

const surfaceContent = {
  "data-sources": {
    title: "Data Sources",
    summary: "Create, inspect, and run simulator sources from one clear project view.",
    note: "This screen will hold the source list, source actions, and entry points into creation and detail flows.",
  },
  recordings: {
    title: "Recordings & Samples",
    summary: "Work with captured and reusable data prepared for replay.",
    note: "This screen will hold recordings, samples, imports, and actions that connect them back into runtime work.",
  },
  scenarios: {
    title: "Scenarios",
    summary: "Build and run repeatable simulator flows.",
    note: "This screen will hold saved scenarios, run entry points, and the path into scenario execution.",
  },
  evidence: {
    title: "Evidence",
    summary: "Review exported runtime results and their traceability.",
    note: "This screen will hold evidence history, filters, and links back to the runs and sources that produced each artifact.",
  },
  evidenceDetail: {
    title: "Evidence Detail",
    summary: "Review one evidence artifact and understand what happened.",
    note: "This screen will hold the artifact summary, timeline, clients, faults, export options, and recovery states.",
  },
  activity: {
    title: "Activity",
    summary: "Track what changed, who did it, and where it happened.",
    note: "This screen will hold the activity stream, filtering, and links back into the affected project areas.",
  },
  settings: {
    title: "Settings",
    summary: "Manage project configuration and operational preferences.",
    note: "This screen will hold editable settings, protected changes, and configuration review.",
  },
  admin: {
    title: "Admin",
    summary: "Manage access and shared usage rules.",
    note: "This screen will hold user management, roles, and shared-environment controls for administrators.",
  },
} as const;

const entrySurfaceContent = {
  projectCreate: {
    title: "Create Project",
    summary: "Start a new saved simulator setup.",
    note: "This surface will hold the guided project creation flow and its naming, validation, and save behavior.",
  },
  projectImport: {
    title: "Import Project",
    summary: "Bring an existing simulator setup into this environment.",
    note: "This surface will hold version checks, import validation, and the project import result flow.",
  },
} as const;

export const router = createBrowserRouter([
  {
    path: "/projects",
    element: <ProjectEntryPage />,
  },
  {
    path: "/projects/create",
    element: <SurfaceStubPage {...entrySurfaceContent.projectCreate} />,
  },
  {
    path: "/projects/import",
    element: <SurfaceStubPage {...entrySurfaceContent.projectImport} />,
  },
  {
    path: "/",
    element: <AppShell />,
    children: [
      { index: true, element: <Navigate to="/projects" replace /> },
      { path: "overview", element: <OverviewPage /> },
      {
        path: "data-sources/new",
        element: <CreateDataSourceWizardPage />,
      },
      {
        path: "data-sources",
        element: <DataSourcesListPage />,
      },
      {
        path: "data-sources/:sourceId",
        element: <DataSourceDetailPreviewPage />,
      },
      {
        path: "data-sources/:sourceId/record",
        element: <RecordingFlowPage />,
      },
      {
        path: "data-sources/:sourceId/replay",
        element: <ReplayFlowPage />,
      },
      {
        path: "recordings",
        element: <SurfaceStubPage {...surfaceContent.recordings} />,
      },
      {
        path: "scenarios",
        element: <SurfaceStubPage {...surfaceContent.scenarios} />,
      },
      {
        path: "evidence",
        element: <EvidenceListPage />,
      },
      {
        path: "evidence/:evidenceId",
        element: <SurfaceStubPage {...surfaceContent.evidenceDetail} />,
      },
      {
        path: "activity",
        element: <SurfaceStubPage {...surfaceContent.activity} />,
      },
      {
        path: "settings",
        element: <SettingsPage />,
      },
      {
        path: "admin",
        element: <SurfaceStubPage {...surfaceContent.admin} adminOnly />,
      },
      {
        path: "notifications-demo",
        element: <NotificationDemoPage adminOnly />,
      },
    ],
  },
]);
