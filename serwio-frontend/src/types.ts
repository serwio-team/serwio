// Typy odpowiadają rekordom DTO z serwio-backend — te same nazwy i pola.
// UUID przychodzą jako string, OffsetDateTime jako string ISO 8601.

// serwio.account.dto.UserAccountResponse
export interface UserAccountResponse {
  id: string;
  email: string;
  role: string;
  status: string;
  createdAt: string;
}

// serwio.owner.dto.AssetOwnerResponse
export interface AssetOwnerResponse {
  id: string;
  userId: string;
  name: string;
  createdAt: string;
}

// serwio.location.dto.LocationListItemResponse
export interface LocationListItemResponse {
  id: string;
  name: string;
  addressLine: string;
  city: string;
}

// serwio.location.dto.LocationRequest
export interface LocationRequest {
  name: string;
  addressLine: string;
  city: string;
  postalCode: string;
  country: string;
  timezone: string;
}

// serwio.location.dto.LocationResponse
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

// serwio.asset.dto.ServiceObjectListItemResponse
export interface ServiceObjectListItemResponse {
  id: string;
  name: string;
  type: string;
  locationId: string;
  locationName: string;
}

// serwio.asset.dto.ServiceObjectRequest
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

// serwio.asset.dto.ServiceObjectResponse
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
