package bizappz.daypurse.ui.budget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bizappz.daypurse.R
import bizappz.daypurse.domain.budget.BudgetState
import bizappz.daypurse.ui.appViewModel
import bizappz.daypurse.ui.components.BudgetBadge
import bizappz.daypurse.ui.components.SectionTitle
import bizappz.daypurse.ui.components.groupedRow

/** Settings → Budgets: every budget, with ended ones listed separately. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(onBack: () -> Unit, onOpen: (Long) -> Unit, onNew: () -> Unit) {
    val vm = appViewModel { BudgetsViewModel(it.budgetEngine, it.todayProvider) }
    val states by vm.states.collectAsStateWithLifecycle()
    val all = states ?: return
    val ended = all.filterIsInstance<BudgetState.Ended>()
    val current = all - ended.toSet()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.budgets_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(painterResource(R.drawable.ic_add), null) },
                text = { Text(stringResource(R.string.budget_new_title)) },
                shape = MaterialTheme.shapes.large,
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)) {
            itemsIndexed(current, key = { _, s -> s.budget?.id ?: 0L }) { i, s -> BudgetRow(s, i, current.size, onOpen) }
            if (ended.isNotEmpty()) {
                item(key = "ended") { SectionTitle(stringResource(R.string.budget_ended_section), Modifier.padding(start = 16.dp, top = 8.dp)) }
                itemsIndexed(ended, key = { _, s -> s.budget?.id ?: 0L }) { i, s -> BudgetRow(s, i, ended.size, onOpen) }
            }
        }
    }
}

@Composable
private fun BudgetRow(state: BudgetState, index: Int, count: Int, onOpen: (Long) -> Unit) {
    val budget = state.budget ?: return
    ListItem(
        leadingContent = { BudgetBadge(budget) },
        headlineContent = { Text(budget.name) },
        supportingContent = { Text(budgetStatus(state)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.groupedRow(index, count).clickable { onOpen(budget.id) },
    )
}
