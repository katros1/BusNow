import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:lucide_icons/lucide_icons.dart';
import 'package:client/core/theme/app_colors.dart';
import 'package:client/core/widgets/app_widgets.dart';

class SettingsPage extends StatelessWidget {
  const SettingsPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      body: CustomScrollView(
        slivers: [
          _buildSliverHeader(context),
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(24, 32, 24, 120),
              child: Column(
                children: [
                  _buildProfileSummary(),
                  const SizedBox(height: 48),
                  const SectionHeader(title: 'Experience'),
                  _buildSettingsGroup([
                    _SettingsTile(icon: LucideIcons.moon, title: 'Dark Appearance', trailing: Switch(value: true, activeColor: AppColors.primary, onChanged: (v) {})),
                    const _SettingsTile(icon: LucideIcons.bell, title: 'Smart Trip Alerts', subtitle: 'Active for Line 402'),
                    const _SettingsTile(icon: LucideIcons.map, title: 'Map Tile Preferences', subtitle: 'Esri Satellite'),
                  ]),
                  const SizedBox(height: 32),
                  const SectionHeader(title: 'Account & Security'),
                  _buildSettingsGroup([
                    const _SettingsTile(icon: LucideIcons.creditCard, title: 'Urban Wallet', subtitle: 'FRW 12,500 balance'),
                    const _SettingsTile(icon: LucideIcons.shield, title: 'Privacy Policy'),
                    const _SettingsTile(icon: LucideIcons.logOut, title: 'Sign Out', titleColor: AppColors.error),
                  ]),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSliverHeader(BuildContext context) {
    return SliverAppBar(
      expandedHeight: 120,
      backgroundColor: AppColors.background,
      elevation: 0,
      pinned: true,
      centerTitle: false,
      title: const Text('Settings', style: TextStyle(fontWeight: FontWeight.bold, color: AppColors.onSurface)),
    );
  }

  Widget _buildProfileSummary() {
    return Container(
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [AppColors.onSurface, Color(0xFF2C3E50)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(30),
      ),
      child: Row(
        children: [
          Container(
            width: 60,
            height: 60,
            decoration: BoxDecoration(
              color: Colors.white.withOpacity(0.1),
              shape: BoxShape.circle,
            ),
            child: const Icon(LucideIcons.user, color: Colors.white, size: 30),
          ),
          const SizedBox(width: 20),
          const Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Alex Commuter',
                  style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 18),
                ),
                Text(
                  'verified_kigali_id',
                  style: TextStyle(color: Colors.white60, fontSize: 12),
                ),
              ],
            ),
          ),
          const Icon(LucideIcons.chevronRight, color: Colors.white54),
        ],
      ),
    ).animate().fadeIn(duration: 600.ms).scale(begin: const Offset(0.95, 0.95));
  }

  Widget _buildSettingsGroup(List<Widget> children) {
    return TonalCard(
      padding: EdgeInsets.zero,
      child: Column(
        children: children.asMap().entries.map((entry) {
          final idx = entry.key;
          final widget = entry.value;
          return Column(
            children: [
              widget,
              if (idx < children.length - 1) 
                Padding(
                  padding: const EdgeInsets.only(left: 56),
                  child: Divider(height: 1, color: AppColors.surfaceContainerHighest.withOpacity(0.5)),
                ),
            ],
          );
        }).toList(),
      ),
    );
  }
}

class _SettingsTile extends StatelessWidget {
  final IconData icon;
  final String title;
  final String? subtitle;
  final Widget? trailing;
  final Color? titleColor;

  const _SettingsTile({
    required this.icon,
    required this.title,
    this.subtitle,
    this.trailing,
    this.titleColor,
  });

  @override
  Widget build(BuildContext context) {
    return ListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
      leading: Container(
        padding: const EdgeInsets.all(10),
        decoration: BoxDecoration(
          color: (titleColor ?? AppColors.primary).withOpacity(0.1),
          borderRadius: BorderRadius.circular(12),
        ),
        child: Icon(icon, color: titleColor ?? AppColors.primary, size: 20),
      ),
      title: Text(
        title,
        style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15, color: titleColor ?? AppColors.onSurface),
      ),
      subtitle: subtitle != null 
          ? Text(subtitle!, style: const TextStyle(fontSize: 12, color: AppColors.onSurfaceVariant)) 
          : null,
      trailing: trailing ?? const Icon(LucideIcons.chevronRight, size: 16),
      onTap: trailing == null ? () {} : null,
    );
  }
}
