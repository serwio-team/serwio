export interface UserAccountResponse {
  id: string;
  email: string;
  role: string;
  status: string;
  passwordChangeRequired: boolean;
  createdAt: string;
}

export interface OwnerRegistrationRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  confirmPassword: string;
}

export interface AssetOwnerResponse {
  id: string;
  userId: string;
  name: string;
  createdAt: string;
}

export interface LocationListItemResponse {
  id: string;
  name: string;
  addressLine: string;
  city: string;
}

export interface AvailabilityInterval {
  from: string;
  to: string;
}

export interface AvailabilityDay {
  dayOfWeek: number;
  intervals: AvailabilityInterval[];
}

export interface AvailabilityRequest {
  days: AvailabilityDay[];
}

export interface AvailabilityResponse extends AvailabilityRequest {
  locationId: string;
  timezone: string;
}

export interface LocationRequest {
  name: string;
  addressLine: string;
  city: string;
  postalCode: string;
  country: string;
  timezone: string;
}

export interface LocationResponse {
  id: string;
  assetOwnerId: string;
  name: string;
  addressLine: string;
  city: string;
  postalCode: string;
  country: string;
  timezone: string;
}

export interface ServiceObjectListItemResponse {
  id: string;
  name: string;
  type: string;
  locationId: string;
  locationName: string;
}

export interface ServiceObjectRequest {
  locationId: string;
  name: string;
  type: string;
  description: string | null;
  manufacturer: string | null;
  model: string | null;
  serialNumber: string | null;
  attributes: Record<string, unknown> | null;
}

export interface ServiceObjectResponse {
  id: string;
  locationId: string;
  locationName: string;
  name: string;
  type: string;
  description: string | null;
  manufacturer: string | null;
  model: string | null;
  serialNumber: string | null;
  attributes: Record<string, unknown> | null;
  publicId: string | null;
  qrGeneratedAt: string | null;
  active: boolean;
  createdAt: string;
}

export interface ServiceCompanyResponse {
  id: string;
  userId: string;
  name: string;
  createdAt: string;
}

export interface TechnicianProfileResponse {
  id: string;
  userId: string;
  email: string;
  displayName: string;
  branchId: string;
  branchName: string;
  companyId: string;
  companyName: string;
  createdAt: string;
}

export interface PasswordChangeRequest {
  currentPassword: string;
  newPassword: string;
}

export interface CompanySummaryResponse {
  activeBranchCount: number;
  inactiveBranchCount: number;
  activeTechnicianCount: number;
  pendingTechnicianCount: number;
  inactiveTechnicianCount: number;
}

export interface ServiceBranchRequest {
  name: string;
}

export interface ServiceBranchResponse {
  id: string;
  name: string;
  active: boolean;
  activeTechnicianCount: number;
  createdAt: string;
}

export type TechnicianAccountStatus = 'PENDING' | 'ACTIVE' | 'INACTIVE';

export interface TechnicianResponse {
  id: string;
  userId: string;
  email: string;
  displayName: string;
  branchId: string;
  branchName: string;
  active: boolean;
  accountStatus: TechnicianAccountStatus;
  createdAt: string;
}

export interface TechnicianCreateRequest {
  email: string;
  displayName: string;
  branchId: string;
  password: string;
}

export interface TechnicianUpdateRequest {
  displayName: string;
  branchId: string;
  active: boolean;
}

export interface TechnicianPasswordRequest {
  password: string;
}

export type ServiceOrderType = 'REPAIR' | 'INSPECTION';

export type ServiceOrderStatus =
  | 'NEW'
  | 'ASSIGNED'
  | 'AWAITING_SCHEDULING'
  | 'VISIT_CONFIRMED'
  | 'IN_PROGRESS'
  | 'REVISIT_REQUIRED'
  | 'COMPLETED'
  | 'AWAITING_TECHNICIAN_REVIEW'
  | 'AWAITING_OWNER_CONFIRMATION'
  | 'CLOSED'
  | 'CANCELLED';

export interface ServiceOrderRequest {
  serviceObjectId: string;
  orderType: ServiceOrderType;
  description: string;
}

export interface ServiceOrderListItemResponse {
  id: string;
  serviceObjectId: string;
  serviceObjectName: string;
  locationId: string;
  locationName: string;
  orderType: string;
  currentStatus: string;
  createdAt: string;
}

export interface ServiceOrderResponse {
  id: string;
  serviceObjectId: string;
  serviceObjectName: string;
  locationId: string;
  locationName: string;
  addressLine: string;
  city: string;
  timezone: string;
  assetOwnerId: string;
  assetOwnerName: string;
  serviceCompanyId: string;
  orderType: string;
  description: string;
  currentStatus: string;
  createdByUserId: string;
  createdAt: string;
  closedAt: string | null;
  reportFinalized: boolean;
}

export interface TechnicianServiceOrderResponse extends ServiceOrderListItemResponse {
  addressLine: string;
  city: string;
  timezone: string;
  description: string;
  coordinator: { id: string; displayName: string } | null;
}

export interface OrderAssignmentRequest {
  expectedVersion: number;
  branchIds: string[];
  technicianIds: string[];
  coordinatorTechnicianId: string;
}

export interface OrderAssignmentResponse {
  serviceOrderId: string;
  status: string;
  version: number;
  branches: { id: string; name: string }[];
  technicians: { id: string; displayName: string; branchId: string }[];
  coordinatorTechnicianId: string | null;
}

export interface VisitTimeRange {
  startsAt: string;
  endsAt: string;
}

export interface VisitProposal extends VisitTimeRange {
  id: string;
  proposedBy: 'OWNER' | 'COORDINATOR';
}

export type VisitOutcome = 'COMPLETED' | 'REVISIT_REQUIRED';

export interface ServiceVisit {
  id: string;
  status: 'DRAFT' | 'CONFIRMED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
  coordinatorTechnicianId: string;
  currentProposal: VisitProposal | null;
  startsAt: string | null;
  endsAt: string | null;
  outcome: VisitOutcome | null;
}

export interface ServiceVisitsResponse {
  timezone: string;
  availability: AvailabilityDay[];
  visits: ServiceVisit[];
}

export type VisitAction =
  | { action: 'propose'; visitId: string; time: VisitTimeRange }
  | { action: 'counter'; visitId: string; proposalId: string; time: VisitTimeRange }
  | { action: 'accept' | 'reject'; visitId: string; proposalId: string }
  | { action: 'start'; visitId: string }
  | { action: 'complete'; visitId: string; outcome: VisitOutcome };

export interface ReportDraftRequest {
  actionsDone: string | null;
  diagnosis: string | null;
  resolution: string | null;
  usedMaterials: string | null;
  recommendations: string | null;
}

export interface ServiceReport extends ReportDraftRequest {
  id: string;
  serviceOrderId: string;
  authorTechnicianId: string;
  createdAt: string;
  updatedAt: string;
  finalizedAt: string | null;
}
