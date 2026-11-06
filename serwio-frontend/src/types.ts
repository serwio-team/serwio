export interface UserAccountResponse {
  id: string;
  email: string;
  role: string;
  status: string;
  passwordChangeRequired: boolean;
  createdAt: string;
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
}
