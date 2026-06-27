import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useRegisterTenantMutation } from '../api';
import { Shield, Plus, Loader2, CheckCircle2 } from 'lucide-react';
import { useState } from 'react';

const tenantSchema = z.object({
  name: z.string().min(3, 'Clinic name must be at least 3 characters'),
  slug: z.string()
    .min(3, 'Slug must be at least 3 characters')
    .regex(/^[a-z0-9-]+$/, 'Slug can only contain lowercase letters, numbers, and hyphens'),
});

type TenantFormData = z.infer<typeof tenantSchema>;

export function TenantCreateForm() {
  const [registerTenant, { isLoading, error }] = useRegisterTenantMutation();
  const [success, setSuccess] = useState<string | null>(null);

  const { register, handleSubmit, setValue, formState: { errors } } = useForm<TenantFormData>({
    resolver: zodResolver(tenantSchema),
  });

  const onSubmit = async (data: TenantFormData) => {
    try {
      setSuccess(null);
      const res = await registerTenant(data).unwrap();
      setSuccess(`Tenant "${res.name}" registered successfully with slug: ${res.slug}`);
      setValue('name', '');
      setValue('slug', '');
    } catch (err) {
      // Handled by RTK query state
    }
  };

  const handleNameChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const name = e.target.value;
    const generatedSlug = name
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/(^-|-$)+/g, '');
    setValue('slug', generatedSlug, { shouldValidate: true });
  };

  return (
    <div className="w-full max-w-lg p-8 rounded-2xl bg-white border border-slate-200/80 shadow-xl shadow-slate-100/50">
      <div className="flex items-center mb-6 space-x-3">
        <div className="p-2.5 bg-teal-50 border border-teal-100 text-teal-600 rounded-xl">
          <Shield className="w-6 h-6" />
        </div>
        <div>
          <h3 className="text-xl font-bold text-slate-800">Create New Tenant</h3>
          <p className="text-slate-500 text-xs mt-0.5">Register a new isolated clinic domain</p>
        </div>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-5">
        <div>
          <label htmlFor="name" className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1.5">Clinic Name</label>
          <input
            {...register('name')}
            onChange={(e) => {
              register('name').onChange(e);
              handleNameChange(e);
            }}
            type="text"
            id="name"
            placeholder="Maple Health Clinic"
            className="block w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-teal-500/30 focus:border-teal-500 transition-all"
          />
          {errors.name && <p className="text-red-500 text-xs mt-1.5">{errors.name.message}</p>}
        </div>

        <div>
          <label htmlFor="slug" className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1.5">Domain Slug</label>
          <div className="flex rounded-xl bg-slate-50 border border-slate-200 focus-within:ring-2 focus-within:ring-teal-500/30 focus-within:border-teal-500 transition-all">
            <span className="flex items-center pl-4 pr-1 text-slate-400 text-sm select-none">/</span>
            <input
              {...register('slug')}
              type="text"
              id="slug"
              placeholder="maple-health"
              className="block w-full py-2.5 pr-4 bg-transparent border-0 text-slate-800 placeholder-slate-400 focus:outline-none"
            />
          </div>
          <p className="text-[10px] text-slate-400 mt-1">This forms the unique login route: /maple-health/dashboard</p>
          {errors.slug && <p className="text-red-500 text-xs mt-1.5">{errors.slug.message}</p>}
        </div>

        {error && (
          <div className="p-3 bg-red-50 border border-red-100 rounded-xl text-red-600 text-xs">
            <span>{(error as any).data?.message || 'Failed to create tenant.'}</span>
          </div>
        )}

        {success && (
          <div className="p-3.5 bg-emerald-50 border border-emerald-100 rounded-xl flex items-start text-emerald-600 text-xs">
            <CheckCircle2 className="w-4 h-4 mr-2.5 shrink-0" />
            <span>{success}</span>
          </div>
        )}

        <button
          type="submit"
          disabled={isLoading}
          className="w-full flex items-center justify-center py-2.5 px-4 bg-teal-600 hover:bg-teal-700 text-white font-semibold rounded-xl transition-all shadow-md shadow-teal-500/10 active:scale-[0.98] disabled:opacity-50"
        >
          {isLoading ? (
            <>
              <Loader2 className="w-5 h-5 mr-2 animate-spin" />
              Creating...
            </>
          ) : (
            <>
              <Plus className="w-5 h-5 mr-2" />
              Create Tenant
            </>
          )}
        </button>
      </form>
    </div>
  );
}
