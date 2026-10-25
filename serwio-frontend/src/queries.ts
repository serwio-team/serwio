// Zapytania i mutacje TanStack Query. Klucze zaczynają się od nazwy zasobu z backendu,
// więc unieważnienie ['locations'] odświeża listy, pełną listę i szczegóły naraz.

import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError, PAGE_SIZE, api } from './api';
import type { LocationRequest, PasswordChangeRequest, ServiceObjectRequest } from './types';

export const queryKeys = {
  currentUser: ['users', 'me'] as const,
  currentOwner: ['owners', 'me'] as const,
  currentCompany: ['companies', 'me'] as const,
  currentTechnician: ['technicians', 'me'] as const,
  locations: ['locations'] as const,
  locationList: ['locations', 'list'] as const,
  allLocations: ['locations', 'all'] as const,
  location: (id: string) => ['locations', 'detail', id] as const,
  serviceObjects: ['service-objects'] as const,
  serviceObjectList: (locationId?: string) => ['service-objects', 'list', locationId ?? 'all'] as const,
  serviceObject: (id: string) => ['service-objects', 'detail', id] as const,
};

// Strona krótsza niż PAGE_SIZE oznacza koniec listy.
function nextPage<T>(lastPage: T[], pages: T[][]): number | undefined {
  return lastPage.length < PAGE_SIZE ? undefined : pages.length;
}

export function useCurrentUser() {
  return useQuery({ queryKey: queryKeys.currentUser, queryFn: api.currentUser });
}

export function useCurrentOwner(enabled = true) {
  return useQuery({ queryKey: queryKeys.currentOwner, queryFn: api.currentOwner, enabled });
}

export function useCurrentCompany(enabled = true) {
  return useQuery({ queryKey: queryKeys.currentCompany, queryFn: api.currentCompany, enabled });
}

export function useCurrentTechnician(enabled = true) {
  return useQuery({ queryKey: queryKeys.currentTechnician, queryFn: api.currentTechnician, enabled });
}

export function useChangeOwnPassword() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: PasswordChangeRequest) => api.changeOwnPassword(data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.currentUser }),
  });
}

export function useLocationList() {
  return useInfiniteQuery({
    queryKey: queryKeys.locationList,
    queryFn: ({ pageParam }) => api.listLocations(pageParam),
    initialPageParam: 0,
    getNextPageParam: nextPage,
  });
}

// Wszystkie lokalizacje — do filtra i listy wyboru w formularzu urządzenia.
export function useAllLocations() {
  return useQuery({ queryKey: queryKeys.allLocations, queryFn: api.allLocations });
}

export function useLocation(id: string | undefined) {
  return useQuery({
    queryKey: queryKeys.location(id ?? ''),
    queryFn: () => api.getLocation(id!),
    enabled: Boolean(id),
  });
}

export function useServiceObjectList(locationId: string | undefined) {
  return useInfiniteQuery({
    queryKey: queryKeys.serviceObjectList(locationId),
    queryFn: ({ pageParam }) => api.listServiceObjects(pageParam, locationId),
    initialPageParam: 0,
    getNextPageParam: nextPage,
  });
}

export function useServiceObject(id: string | undefined) {
  return useQuery({
    queryKey: queryKeys.serviceObject(id ?? ''),
    queryFn: () => api.getServiceObject(id!),
    enabled: Boolean(id),
  });
}

export function useSaveLocation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id?: string; data: LocationRequest }) =>
      id ? api.updateLocation(id, data) : api.createLocation(data),
    onSuccess: (_saved, { id }) => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.locations });
      // Nazwa lokalizacji jest też widoczna na liście urządzeń.
      if (id) void queryClient.invalidateQueries({ queryKey: queryKeys.serviceObjects });
    },
    onError: (error) => {
      if (isNotFound(error)) void queryClient.invalidateQueries({ queryKey: queryKeys.locations });
    },
  });
}

const isNotFound = (error: unknown) => error instanceof ApiError && error.status === 404;

export function useDeleteLocation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.deleteLocation(id),
    // Także po 404/409 — lista mogła się zmienić w międzyczasie.
    onSettled: () => queryClient.invalidateQueries({ queryKey: queryKeys.locations }),
  });
}

export function useSaveServiceObject() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id?: string; data: ServiceObjectRequest }) =>
      id ? api.updateServiceObject(id, data) : api.createServiceObject(data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.serviceObjects }),
    // 404 może dotyczyć urządzenia albo wybranej lokalizacji — odświeżamy obie listy.
    onError: (error) => {
      if (!isNotFound(error)) return;
      void queryClient.invalidateQueries({ queryKey: queryKeys.serviceObjects });
      void queryClient.invalidateQueries({ queryKey: queryKeys.locations });
    },
  });
}

export function useDeleteServiceObject() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.deleteServiceObject(id),
    onSettled: () => queryClient.invalidateQueries({ queryKey: queryKeys.serviceObjects }),
  });
}
