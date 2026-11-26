import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError, PAGE_SIZE, api } from './api';
import type {
  AvailabilityRequest,
  LocationRequest,
  PasswordChangeRequest,
  ReportDraftRequest,
  ServiceBranchRequest,
  ServiceObjectRequest,
  ServiceOrderRequest,
  ServiceOrderResponse,
  TechnicianCreateRequest,
  TechnicianPasswordRequest,
  TechnicianServiceOrderResponse,
  TechnicianUpdateRequest,
  VisitAction,
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
  availability: (id: string) => ['locations', 'availability', id] as const,
  serviceObjects: ['service-objects'] as const,
  serviceObjectList: (locationId?: string) => ['service-objects', 'list', locationId ?? 'all'] as const,
  serviceObject: (id: string) => ['service-objects', 'detail', id] as const,
  serviceOrders: ['service-orders'] as const,
  serviceOrderList: (statuses: string[]) => ['service-orders', 'list', statuses.join(',')] as const,
  companyOrderList: (status?: string) => ['service-orders', 'company', status ?? 'all'] as const,
  serviceOrder: (id: string) => ['service-orders', 'detail', id] as const,
  technicianOrderList: (userId: string) => ['technician-orders', userId, 'list'] as const,
  technicianOrder: (userId: string, id: string) => ['technician-orders', userId, 'detail', id] as const,
  serviceVisits: (userId: string, orderId: string) => ['service-visits', userId, orderId] as const,
  serviceReport: (userId: string, orderId: string) => ['service-report', userId, orderId] as const,
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

export function useAvailability(id: string) {
  return useQuery({
    queryKey: queryKeys.availability(id),
    queryFn: () => api.getAvailability(id),
    refetchOnWindowFocus: false,
  });
}

export function useSaveAvailability(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: AvailabilityRequest) => api.saveAvailability(id, data),
    onSuccess: (saved) => queryClient.setQueryData(queryKeys.availability(id), saved),
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

export function useServiceOrderList(statuses: string[]) {
  return useInfiniteQuery({
    queryKey: queryKeys.serviceOrderList(statuses),
    queryFn: ({ pageParam }) => api.listServiceOrders(statuses, pageParam),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => lastPage.nextPage,
  });
}

export function useCompanyServiceOrderList(status: string | undefined) {
  return useInfiniteQuery({
    queryKey: queryKeys.companyOrderList(status),
    queryFn: ({ pageParam }) => api.listCompanyServiceOrders(pageParam, status),
    initialPageParam: 0,
    getNextPageParam: nextPage,
  });
}

export function useTechnicianServiceOrderList(userId: string) {
  return useInfiniteQuery({
    queryKey: queryKeys.technicianOrderList(userId),
    queryFn: ({ pageParam }) => api.listTechnicianServiceOrders(pageParam),
    initialPageParam: 0,
    getNextPageParam: nextPage,
  });
}

export function useServiceOrder(id: string | undefined, technicianUserId?: string) {
  return useQuery<ServiceOrderResponse | TechnicianServiceOrderResponse>({
    queryKey: technicianUserId ? queryKeys.technicianOrder(technicianUserId, id ?? '') : queryKeys.serviceOrder(id ?? ''),
    queryFn: () => technicianUserId ? api.getTechnicianServiceOrder(id!) : api.getServiceOrder(id!),
    enabled: Boolean(id),
  });
}

export function useServiceVisits(userId: string, orderId: string) {
  return useQuery({
    queryKey: queryKeys.serviceVisits(userId, orderId),
    queryFn: () => api.listServiceVisits(orderId),
  });
}

export function useUpdateServiceVisit(userId: string, orderId: string) {
  const queryClient = useQueryClient();
  const refresh = () => Promise.all([
    queryClient.invalidateQueries({ queryKey: queryKeys.serviceVisits(userId, orderId) }),
    queryClient.invalidateQueries({ queryKey: queryKeys.technicianOrder(userId, orderId) }),
    queryClient.invalidateQueries({ queryKey: queryKeys.technicianOrderList(userId) }),
  ]);

  return useMutation({
    mutationFn: (action: VisitAction) => api.updateServiceVisit(orderId, action),
    onSuccess: refresh,
    onError: (error) => {
      if (error instanceof ApiError && (error.status === 403 || error.status === 404 || error.status === 409)) {
        return refresh();
      }
    },
  });
}

export function useServiceReport(userId: string, orderId: string) {
  return useQuery({
    queryKey: queryKeys.serviceReport(userId, orderId),
    queryFn: async () => {
      try {
        return await api.getServiceReport(orderId);
      } catch (error) {
        if (isNotFound(error)) return null;
        throw error;
      }
    },
  });
}

type ReportUpdate =
  | { action: 'save'; draft: ReportDraftRequest }
  | { action: 'finalize'; draft?: ReportDraftRequest };

export function useUpdateServiceReport(userId: string, orderId: string) {
  const queryClient = useQueryClient();
  const key = queryKeys.serviceReport(userId, orderId);

  return useMutation({
    onMutate: () => queryClient.cancelQueries({ queryKey: key }),
    mutationFn: async (update: ReportUpdate) => {
      if (update.action === 'save') return api.saveReportDraft(orderId, update.draft);
      if (update.draft) {
        const savedDraft = await api.saveReportDraft(orderId, update.draft);
        queryClient.setQueryData(key, savedDraft);
      }
      return api.finalizeReport(orderId);
    },
    onSuccess: async (report) => {
      await queryClient.cancelQueries({ queryKey: key });
      queryClient.setQueryData(key, report);
    },
    onError: (error) => {
      if (error instanceof ApiError && (error.status === 403 || error.status === 404 || error.status === 409)) {
        return Promise.all([
          queryClient.invalidateQueries({ queryKey: key }),
          queryClient.invalidateQueries({ queryKey: queryKeys.technicianOrder(userId, orderId) }),
        ]);
      }
    },
  });
}

export function useCreateServiceOrder() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: ServiceOrderRequest) => api.createServiceOrder(data),
    onSuccess: (created) => {
      queryClient.setQueryData(queryKeys.serviceOrder(created.id), created);
      void queryClient.invalidateQueries({
        queryKey: queryKeys.serviceOrders,
        predicate: (query) => query.queryKey[1] !== 'detail',
      });
    },
    onError: (error) => {
      if (isNotFound(error)) void queryClient.invalidateQueries({ queryKey: queryKeys.serviceObjects });
    },
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
