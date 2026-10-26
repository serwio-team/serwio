import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError, PAGE_SIZE, api } from './api';
import type {
  LocationRequest,
  PasswordChangeRequest,
  ServiceBranchRequest,
  ServiceObjectRequest,
  TechnicianCreateRequest,
  TechnicianPasswordRequest,
  TechnicianUpdateRequest,
} from './types';

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
  companySummary: ['companies', 'me', 'summary'] as const,
  branches: ['branches'] as const,
  branchList: ['branches', 'list'] as const,
  allBranches: ['branches', 'all'] as const,
  branch: (id: string) => ['branches', 'detail', id] as const,
  technicians: ['technicians'] as const,
  technicianList: (branchId?: string) => ['technicians', 'list', branchId ?? 'all'] as const,
  technician: (id: string) => ['technicians', 'detail', id] as const,
};

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
    onSettled: () => queryClient.invalidateQueries({ queryKey: queryKeys.locations }),
  });
}

export function useSaveServiceObject() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id?: string; data: ServiceObjectRequest }) =>
      id ? api.updateServiceObject(id, data) : api.createServiceObject(data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.serviceObjects }),
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

export function useCompanySummary() {
  return useQuery({ queryKey: queryKeys.companySummary, queryFn: api.companySummary });
}

export function useBranchList() {
  return useInfiniteQuery({
    queryKey: queryKeys.branchList,
    queryFn: ({ pageParam }) => api.listBranches(pageParam),
    initialPageParam: 0,
    getNextPageParam: nextPage,
  });
}

export function useAllBranches() {
  return useQuery({ queryKey: queryKeys.allBranches, queryFn: api.allBranches });
}

export function useBranch(id: string | undefined) {
  return useQuery({
    queryKey: queryKeys.branch(id ?? ''),
    queryFn: () => api.getBranch(id!),
    enabled: Boolean(id),
  });
}

export function useTechnicianList(branchId: string | undefined) {
  return useInfiniteQuery({
    queryKey: queryKeys.technicianList(branchId),
    queryFn: ({ pageParam }) => api.listTechnicians(pageParam, branchId),
    initialPageParam: 0,
    getNextPageParam: nextPage,
  });
}

export function useTechnician(id: string | undefined) {
  return useQuery({
    queryKey: queryKeys.technician(id ?? ''),
    queryFn: () => api.getTechnician(id!),
    enabled: Boolean(id),
  });
}

function useInvalidateCompanyData() {
  const queryClient = useQueryClient();
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.companySummary }),
      queryClient.invalidateQueries({ queryKey: queryKeys.branches }),
      queryClient.invalidateQueries({ queryKey: queryKeys.technicians }),
    ]);
}

export function useSaveBranch() {
  const invalidate = useInvalidateCompanyData();
  return useMutation({
    mutationFn: ({ id, data }: { id?: string; data: ServiceBranchRequest }) =>
      id ? api.updateBranch(id, data) : api.createBranch(data),
    onSuccess: invalidate,
    onError: (error) => {
      if (isNotFound(error)) void invalidate();
    },
  });
}

export function useDeactivateBranch() {
  const invalidate = useInvalidateCompanyData();
  return useMutation({
    mutationFn: (id: string) => api.deactivateBranch(id),
    onSettled: invalidate,
  });
}

export function useCreateTechnician() {
  const invalidate = useInvalidateCompanyData();
  return useMutation({
    mutationFn: (data: TechnicianCreateRequest) => api.createTechnician(data),
    onSuccess: invalidate,
    onError: (error) => {
      if (isNotFound(error)) void invalidate();
    },
  });
}

export function useUpdateTechnician() {
  const invalidate = useInvalidateCompanyData();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: TechnicianUpdateRequest }) => api.updateTechnician(id, data),
    onSettled: invalidate,
  });
}

export function useSetTechnicianPassword() {
  const invalidate = useInvalidateCompanyData();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: TechnicianPasswordRequest }) =>
      api.setTechnicianPassword(id, data),
    onSettled: invalidate,
  });
}
